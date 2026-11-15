package fr.codetics.ev_server.ocpp.v201

import fr.codetics.ev_server.core.AppProperties
import java.math.BigInteger
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.ocsp.CertID
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.UnknownStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

enum class OcspState { GOOD, REVOKED, UNKNOWN, FAILED }

data class OcspOutcome(val state: OcspState, val response: String? = null, val reason: String? = null)

@Component
class OcspClient(private val properties: AppProperties) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build()

    fun status(data: OCSPRequestData): OcspOutcome =
        try {
            val target = URI(data.responderURL)
            if (target.scheme !in setOf("http", "https") || target.host.isNullOrBlank()) {
                return OcspOutcome(OcspState.FAILED, reason = "InvalidURL")
            }
            if (!properties.ocpp.ocspAllowPrivate && InetAddress.getAllByName(target.host).any(::internal)) {
                return OcspOutcome(OcspState.FAILED, reason = "PrivateAddress")
            }
            val algorithm =
                when (data.hashAlgorithm) {
                    HashAlgorithmEnum.SHA256 -> NISTObjectIdentifiers.id_sha256
                    HashAlgorithmEnum.SHA384 -> NISTObjectIdentifiers.id_sha384
                    HashAlgorithmEnum.SHA512 -> NISTObjectIdentifiers.id_sha512
                }
            val id =
                CertificateID(
                    CertID(
                        AlgorithmIdentifier(algorithm, DERNull.INSTANCE),
                        DEROctetString(hex(data.issuerNameHash)),
                        DEROctetString(hex(data.issuerKeyHash)),
                        ASN1Integer(BigInteger(data.serialNumber, 16)),
                    )
                )
            val request =
                HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/ocsp-request")
                    .header("Accept", "application/ocsp-response")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(OCSPReqBuilder().addRequest(id).build().encoded))
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            val body = response.body()
            if (response.statusCode() != 200 || body.size > MAXIMUM_RESPONSE) {
                return OcspOutcome(OcspState.FAILED, reason = "Responder${response.statusCode()}")
            }
            val parsed = OCSPResp(body)
            if (parsed.status != OCSPResp.SUCCESSFUL) return OcspOutcome(OcspState.FAILED, reason = "OcspStatus${parsed.status}")
            val single = (parsed.responseObject as BasicOCSPResp).responses.firstOrNull { it.certID == id }
                ?: return OcspOutcome(OcspState.FAILED, reason = "NoMatchingResponse")
            val state =
                when (single.certStatus) {
                    null -> OcspState.GOOD
                    is RevokedStatus -> OcspState.REVOKED
                    is UnknownStatus -> OcspState.UNKNOWN
                    else -> OcspState.UNKNOWN
                }
            val encoded = Base64.getEncoder().encodeToString(body)
            if (encoded.length > 5500) OcspOutcome(OcspState.FAILED, reason = "ResponseTooLarge")
            else OcspOutcome(state, encoded)
        } catch (error: Exception) {
            OcspOutcome(OcspState.FAILED, reason = "Unreachable")
        }

    private fun internal(address: InetAddress) =
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress

    private fun hex(value: String): ByteArray = BigInteger(value, 16).toByteArray().let { bytes ->
        val length = (value.length + 1) / 2
        when {
            bytes.size == length -> bytes
            bytes.size > length -> bytes.copyOfRange(bytes.size - length, bytes.size)
            else -> ByteArray(length - bytes.size) + bytes
        }
    }

    companion object {
        const val MAXIMUM_RESPONSE = 16384
    }
}

data class ContractOutcome(val status: Iso15118EVCertificateStatusEnum, val exiResponse: String, val reason: String? = null)

@Component
class ContractCertificateClient(private val properties: AppProperties) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val json = JsonMapper.builder().build()

    val configured: Boolean
        get() = properties.ocpp.contractServiceUrl.isNotBlank()

    fun forward(stationId: String, request: Get15118EVCertificateRequest): ContractOutcome {
        if (!configured) return ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, "", "NoContractService")
        return try {
            val body =
                json.writeValueAsString(
                    mapOf(
                        "stationId" to stationId,
                        "iso15118SchemaVersion" to request.iso15118SchemaVersion,
                        "action" to request.action.name,
                        "exiRequest" to request.exiRequest,
                    )
                )
            val builder =
                HttpRequest.newBuilder(URI(properties.ocpp.contractServiceUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
            properties.ocpp.contractServiceToken.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, "", "Service${response.statusCode()}")
            }
            val answer = json.readTree(response.body())
            val exi = answer.path("exiResponse").asText("")
            val accepted = answer.path("status").asText() == "Accepted" && exi.isNotEmpty() && exi.length <= 5600
            if (accepted) ContractOutcome(Iso15118EVCertificateStatusEnum.Accepted, exi)
            else ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, exi.take(5600), "ServiceFailed")
        } catch (_: Exception) {
            ContractOutcome(Iso15118EVCertificateStatusEnum.Failed, "", "Unreachable")
        }
    }
}
