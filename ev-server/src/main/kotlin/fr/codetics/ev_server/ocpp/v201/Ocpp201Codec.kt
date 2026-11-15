package fr.codetics.ev_server.ocpp.v201

import com.fasterxml.jackson.annotation.JsonInclude
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.ocpp.OcppFault
import fr.codetics.ev_server.ocpp.OcppTime
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Size
import kotlin.reflect.KClass
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.LogicalType
import tools.jackson.module.kotlin.KotlinModule

@MustBeDocumented
@Constraint(validatedBy = [DateTimeValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DateTime(
    val message: String = "dateTime",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DateTimeValidator : ConstraintValidator<DateTime, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || runCatching { OcppTime.parse(value) }.isSuccess
}

@MustBeDocumented
@Constraint(validatedBy = [EachMaxLengthValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class EachMaxLength(
    val value: Int,
    val message: String = "maxLength",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class EachMaxLengthValidator : ConstraintValidator<EachMaxLength, List<String>?> {
    private var maximum = 0

    override fun initialize(annotation: EachMaxLength) {
        maximum = annotation.value
    }

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean =
        value == null || value.all { it.length <= maximum }
}

object Ocpp201Codec {
    val mapper: JsonMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            )
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(EnumFeature.READ_ENUMS_USING_TO_STRING, EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .withCoercionConfig(LogicalType.Integer) { strict(it, CoercionInputShape.String, CoercionInputShape.Float, CoercionInputShape.Boolean) }
            .withCoercionConfig(LogicalType.Float) { strict(it, CoercionInputShape.String, CoercionInputShape.Boolean) }
            .withCoercionConfig(LogicalType.Boolean) { strict(it, CoercionInputShape.String, CoercionInputShape.Integer, CoercionInputShape.Float) }
            .withCoercionConfig(LogicalType.Textual) { strict(it, CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean) }
            .withCoercionConfig(LogicalType.Enum) { strict(it, CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.EmptyString) }
            .withCoercionConfig(LogicalType.POJO) { strict(it, CoercionInputShape.EmptyString, CoercionInputShape.String) }
            .withCoercionConfig(LogicalType.Collection) { strict(it, CoercionInputShape.EmptyString, CoercionInputShape.String) }
            .build()

    private val validator: Validator by lazy { Validation.buildDefaultValidatorFactory().validator }

    private fun strict(config: tools.jackson.databind.cfg.MutableCoercionConfig, vararg shapes: CoercionInputShape) {
        shapes.forEach { config.setCoercion(it, CoercionAction.Fail) }
    }

    fun <T : Any> decode(payload: JsonNode, type: KClass<T>): T {
        if (!payload.isObject) {
            throw OcppFault("TypeConstraintViolation", msg("ocpp.payload.not_object"))
        }
        val value =
            try {
                mapper.treeToValue(payload, type.java)
            } catch (error: UnrecognizedPropertyException) {
                throw OcppFault("FormatViolation", msg("ocpp.payload.unknown_property", error.propertyName))
            } catch (error: InvalidNullException) {
                throw OcppFault("OccurrenceConstraintViolation", msg("ocpp.payload.missing", path(error)))
            } catch (error: InvalidFormatException) {
                val code =
                    if (error.targetType?.isEnum == true && error.value is String) "PropertyConstraintViolation"
                    else "TypeConstraintViolation"
                throw OcppFault(code, msg("ocpp.payload.invalid", path(error)))
            } catch (error: MismatchedInputException) {
                val missing = error.message.orEmpty().contains("missing", true)
                throw OcppFault(
                    if (missing) "OccurrenceConstraintViolation" else "TypeConstraintViolation",
                    msg(if (missing) "ocpp.payload.missing" else "ocpp.payload.type", path(error)),
                )
            } catch (error: JacksonException) {
                throw OcppFault("FormatViolation", msg("ocpp.payload.unreadable"))
            }
        validate(value)
        return value
    }

    fun validate(value: Any) {
        val violation = validator.validate(value).minByOrNull { it.propertyPath.toString() } ?: return
        val annotation = violation.constraintDescriptor.annotation
        val collection = violation.invalidValue is Collection<*>
        val code =
            if (annotation is Size && collection) "OccurrenceConstraintViolation" else "PropertyConstraintViolation"
        throw OcppFault(code, msg("ocpp.payload.invalid", violation.propertyPath))
    }

    fun encode(value: Any): JsonNode = mapper.valueToTree(value)

    private fun path(error: JacksonException): String =
        error.path.joinToString(".") { reference -> reference.propertyName ?: reference.index.toString() }.ifBlank { "?" }
}
