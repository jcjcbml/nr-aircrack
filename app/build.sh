#!/bin/sh
# Compila NR AirCrack desde app/.
# Requiere: aapt2, javac, d8, apksigner, python3 y android.jar.
set -e

: "${ANDROID_JAR:?define ANDROID_JAR=/ruta/android.jar}"
: "${KEYSTORE:?define KEYSTORE=/ruta/release.keystore}"
: "${KS_PASS:?define KS_PASS}"
: "${KEY_ALIAS:?define KEY_ALIAS}"
: "${KEY_PASS:?define KEY_PASS}"

AAPT2=${AAPT2:-aapt2}
JAVAC=${JAVAC:-javac}
D8=${D8:-d8}
APKSIGNER=${APKSIGNER:-apksigner}

rm -rf out
mkdir -p out/classes out/gen out/dex

"$AAPT2" compile --dir res -o out/res.zip
"$AAPT2" link -I "$ANDROID_JAR" -o out/base.apk \
    --manifest AndroidManifest.xml \
    --min-sdk-version 26 --target-sdk-version 28 \
    -A assets --java out/gen out/res.zip

"$JAVAC" -source 8 -target 8 -cp "$ANDROID_JAR" -d out/classes \
    out/gen/com/nrcracker/air/R.java src/com/nrcracker/air/*.java

"$D8" --lib "$ANDROID_JAR" --min-api 26 --output out/dex \
    $(find out/classes -name '*.class')

python3 - <<'PY'
import zipfile
z = zipfile.ZipFile("out/base.apk", "a", zipfile.ZIP_DEFLATED)
z.write("out/dex/classes.dex", "classes.dex")
z.close()
PY

"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KEY_PASS" --out out/app.apk out/base.apk
"$APKSIGNER" verify --verbose out/app.apk | head -6
echo "APK: out/app.apk"
