#!/usr/bin/env python3
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "fr.codetics.evapp"
ADB = os.path.expanduser(os.environ.get("ADB", "~/Library/Android/sdk/platform-tools/adb"))
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.environ.get("OUT", os.path.join(ROOT, "..", "screenshots", "android"))


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=check, timeout=120).stdout


def nodes():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/ui.xml", check=False)
        text = adb("exec-out", "cat", "/sdcard/ui.xml", check=False)
        if text.startswith("<?xml"):
            return list(ET.fromstring(text).iter("node"))
        time.sleep(1)
    raise RuntimeError("uiautomator did not return a hierarchy")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(tag=None, text=None):
    for node in nodes():
        if tag and node.get("resource-id") == tag:
            return node
        if text and (node.get("text") == text or node.get("content-desc") == text):
            return node
    return None


def wait(tag=None, text=None, timeout=25):
    deadline = time.time() + timeout
    while time.time() < deadline:
        node = find(tag, text)
        if node is not None:
            return node
        time.sleep(1)
    raise RuntimeError(f"Not found: {tag or text}")


def tap(tag=None, text=None, timeout=25):
    x, y = center(wait(tag, text, timeout))
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.5)


def swipe_up():
    adb("shell", "input", "swipe", "540", "1700", "540", "700", "600")
    time.sleep(1)


def swipe_down():
    adb("shell", "input", "swipe", "540", "700", "540", "1700", "600")
    time.sleep(1)


def scroll_to(tag=None, text=None, attempts=8):
    for _ in range(attempts):
        node = find(tag, text)
        if node is not None and 300 < center(node)[1] < 2050:
            return
        swipe_up()
    raise RuntimeError(f"Cannot scroll to: {tag or text}")


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.5)


def type_text(tag, value):
    tap(tag)
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(1)
    adb("shell", "input", "keyevent", "111")
    time.sleep(1)


def shot(name):
    time.sleep(1.2)
    with open(os.path.join(OUT, f"{name}.png"), "wb") as output:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=output, check=True)
    print("captured", name, flush=True)


def launch(*extras):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "clear", PACKAGE)
    for permission in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "CAMERA", "POST_NOTIFICATIONS"]:
        adb("shell", "pm", "grant", PACKAGE, f"android.permission.{permission}", check=False)
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", "fr-FR", check=False)
    adb("emu", "geo", "fix", "2.3499", "48.853", check=False)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity", *extras)
    time.sleep(6)
    adb("emu", "geo", "fix", "2.3499", "48.853", check=False)


def configuration_gate():
    launch()
    wait(text="À corriger")
    shot("00-application-non-configuree")
    scroll_to("demo.start", attempts=6)
    shot("00b-demonstration-debug")


def guest_flow():
    launch("--ez", "demoAccount", "true", "--ez", "demoGuest", "true")
    wait("tab.account")
    time.sleep(18)
    shot("10-invite-carte")
    tap("tab.charge")
    wait("charge.scan")
    shot("11-invite-recharge")
    tap("charge.scan")
    wait("scan.demo")
    time.sleep(3)
    shot("12-invite-scan")
    tap("scan.demo")
    wait("portal.guest")
    shot("13-invite-point-de-charge")
    tap("portal.guest")
    wait("checkout.pay")
    shot("14-invite-credit")
    tap("checkout.pay")
    wait("guest.stop", timeout=30)
    shot("15-invite-recharge-en-cours")
    tap("tab.account")
    wait("signin.email")
    shot("16-connexion")


def driver_flow():
    launch("--ez", "demoAccount", "true")
    wait("tab.badges")
    time.sleep(18)
    shot("01-carte-bornes")
    tap("map.filter")
    shot("01b-filtres")
    back()
    tap("map.toggle")
    wait(text="Gare de Lyon — Borne A")
    shot("02-liste-bornes")
    tap(text="Gare de Lyon — Borne A")
    wait("connector.start.1")
    shot("03-detail-borne")
    scroll_to("photo.demo")
    tap("photo.demo")
    time.sleep(2)
    shot("04-photos-borne")
    swipe_down()
    swipe_down()
    tap("connector.start.1")
    wait("start.confirm")
    time.sleep(2)
    shot("05-demarrer-charge")
    tap("start.confirm")
    wait("session.gauge", timeout=40)
    time.sleep(6)
    shot("06-charge-temps-reel")
    scroll_to("session.chart")
    shot("06b-charge-courbe")
    tap("tab.map")
    wait("session.accessory")
    shot("09-session-en-cours-accessoire")
    tap("session.accessory")
    wait("session.gauge")
    scroll_to("session.stop")
    tap("session.stop")
    wait("dialog.confirm", text="Arrêter la recharge")
    shot("09b-arret-confirmation")
    nodes_found = [n for n in nodes() if n.get("text") == "Arrêter la recharge" and n.get("resource-id") != "session.stop"]
    x, y = center(nodes_found[-1])
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(6)
    tap("tab.sessions")
    time.sleep(2)
    shot("07-historique")
    tap("sessions.statistics")
    wait("stats.chart")
    shot("08-statistiques")
    tap("sessions.history")
    time.sleep(2)
    tap("session.row.2140")
    time.sleep(3)
    shot("08b-detail-session")
    back()

    tap("tab.badges")
    tap("badges.scan")
    wait(text="Simuler ma carte enregistrée")
    shot("19-nfc-indisponible-simulation")
    tap(text="Simuler ma carte enregistrée")
    wait("nfc.result")
    shot("20-nfc-carte-reconnue")
    tap("badges.scan")
    tap(text="Simuler une carte inconnue")
    wait("nfc.result")
    shot("21-nfc-carte-inconnue")
    scroll_to(text="CARTE-1042")
    tap(text="CARTE-1042")
    wait("wallet.preview")
    shot("22-badge-google-wallet")
    scroll_to("wallet.add")
    shot("23-badge-google-wallet-bouton")
    tap("wallet.add")
    time.sleep(2)
    shot("23b-google-wallet-demo-signature-exploitant")
    back()
    swipe_down()
    swipe_down()
    wait("cards.activate")
    shot("24-commander-activer-carte")
    tap("cards.activate")
    wait("activation.submit")
    shot("25-activer-carte")
    type_text("activation.number", "CARTE-9999")
    tap("activation.submit")
    time.sleep(2)
    shot("26-activer-carte-numero-refuse")
    back()
    tap("cards.activate")
    tap("activation.scan")
    tap(text="Simuler ma carte commandée")
    wait("activation.success")
    shot("27-carte-activee")
    back()
    time.sleep(2)
    scroll_to(text="CARTE-2210")
    shot("28-carte-dans-mes-badges")


def account_flow():
    launch("--ez", "demoAccount", "true")
    tap("tab.account", timeout=40)
    wait("account.vehicles")
    shot("30-compte")
    tap("account.vehicles")
    wait("vehicle.row")
    shot("31-vehicules")
    tap("vehicle.row")
    time.sleep(2)
    shot("31b-detail-vehicule")
    back()
    tap("vehicles.add")
    tap("vehicle.catalog")
    wait(text="Renault Megane E-Tech")
    shot("32b-catalogue-vehicules")
    tap(text="Renault Megane E-Tech")
    time.sleep(1)
    shot("32-ajouter-vehicule")
    scroll_to("vehicle.save")
    tap("vehicle.save")
    wait("vehicle.row")
    time.sleep(2)
    shot("32c-vehicule-ajoute")
    back()
    tap("account.payments")
    time.sleep(2)
    shot("33b-moyens-de-paiement")
    back()
    tap("account.invoices")
    time.sleep(2)
    shot("33-factures")
    back()
    tap("account.notifications")
    wait("notifications.preferences")
    shot("34b-notifications")
    tap("notifications.preferences")
    time.sleep(2)
    shot("34-preferences-notifications")
    back()
    back()
    tap("account.profile")
    time.sleep(2)
    shot("36-profil")
    back()
    scroll_to("account.about")
    tap("account.about")
    wait("about.attribution")
    shot("35-a-propos")
    back()
    scroll_to("account.delete")
    shot("37-supprimer-compte")


def main():
    os.makedirs(OUT, exist_ok=True)
    flows = {"gate": configuration_gate, "guest": guest_flow, "driver": driver_flow, "account": account_flow}
    for name in sys.argv[1:] or flows:
        flows[name]()


if __name__ == "__main__":
    main()
