#!/usr/bin/env bash
# Essai de démarrage sur émulateur : mise à jour depuis la v0.6.1 avec un journal qui contient
# des doublons (comme sur la tablette), puis lancement de la nouvelle version.
set -u
PKG=com.datataxi
OUT=demarrage
mkdir -p "$OUT"

verifier() {
  # $1 = nom du scénario
  sleep 25
  adb logcat -d > "$OUT/$1-logcat.txt"
  adb logcat -d -b crash > "$OUT/$1-plantage.txt" || true
  adb shell run-as $PKG cat files/dernier-plantage.txt > "$OUT/$1-rapport-app.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/$1-ecran.png" || true
  local pid
  pid=$(adb shell pidof $PKG | tr -d '\r')
  if [ -s "$OUT/$1-plantage.txt" ] || [ -z "$pid" ] || grep -q "FATAL EXCEPTION" "$OUT/$1-logcat.txt"; then
    echo "ÉCHEC ($1) : l'application a planté au démarrage"
    grep -A 40 "FATAL EXCEPTION" "$OUT/$1-logcat.txt" | head -60
    exit 1
  fi
  echo "OK ($1) : l'application tourne (pid $pid)"
}

echo "== Scénario 1 : installation neuve, sans journal"
adb uninstall $PKG >/dev/null 2>&1 || true
adb install -r DataTaxi-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity
verifier installation-neuve
adb uninstall $PKG

echo "== Scénario 2 : mise à jour depuis la v0.6.1 avec un journal à doublons"
echo "== Installation de l'ancienne version (v0.6.1)"
curl -sSL -o ancienne.apk https://github.com/zakibelm/Datataxi/releases/download/latest-debug/DataTaxi-debug.apk
adb install -r ancienne.apk
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 5
adb shell am force-stop $PKG

echo "== Journal d'exemple avec doublons (le même message relu plusieurs jours)"
python3 - > journal.tsv <<'PY'
import datetime as dt, random
random.seed(1)
start = dt.date(2026, 10, 1)
lines = []
for d in range(30):
    day = start + dt.timedelta(days=d)
    for k in range(60):
        h, m, s = random.randint(0, 23), random.randint(0, 59), random.randint(0, 59)
        z = random.choice([1, 1, 1, 6, 6, 12, 4, 8, 41])
        t = f"{h:02d}:{m:02d}:{s:02d} Véhicule {random.randint(100, 440)} a reçu sur la Zone {z}"
        lines.append((day, t))
out = []
for day, t in lines:
    out.append(f"{day}\t{t}")
    # relu le lendemain et le surlendemain (le bug de la v0.6.1)
    out.append(f"{day + dt.timedelta(days=1)}\t{t}")
    if random.random() < 0.4:
        out.append(f"{day + dt.timedelta(days=2)}\t{t}")
print("\n".join(out))
PY
wc -l journal.tsv
adb push journal.tsv /data/local/tmp/journal.tsv
adb shell run-as $PKG sh -c "'cat /data/local/tmp/journal.tsv > files/journal.tsv'"
adb shell run-as $PKG ls -la files

echo "== Mise à jour vers la nouvelle version"
adb install -r DataTaxi-debug.apk
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity
verifier mise-a-jour
adb shell run-as $PKG wc -l files/journal.tsv files/journal.avant-nettoyage.tsv > "$OUT/journal-apres-nettoyage.txt" || true
cat "$OUT/journal-apres-nettoyage.txt"
