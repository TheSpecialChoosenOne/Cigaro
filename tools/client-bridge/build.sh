#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRISM="${PRISM_HOME:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher}"
MC_JAR="$PRISM/libraries/com/mojang/minecraft/26.3/minecraft-26.3-client.jar"
LOADER_JAR="$PRISM/libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
JAVA="${CIGARO_JAVA:-$PRISM/java/java-runtime-epsilon/bin/java}"
JAVAC="${CIGARO_JAVAC:-$PRISM/java/java-runtime-epsilon/bin/javac}"

OUT="$HERE/build"
CLASSES="$OUT/classes"
JAR="$OUT/CigaroBridge-1.0.0.jar"

for f in "$MC_JAR" "$LOADER_JAR"; do
  [[ -f "$f" ]] || { echo "missing: $f" >&2; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$CLASSES"

CP="$MC_JAR:$LOADER_JAR"
while IFS= read -r jar; do
  CP="$CP:$jar"
done < <(find "$PRISM/libraries" -name '*.jar' \
            -not -path '*/net/fabricmc/intermediary/*' | sort)

echo "Compiling..."
find "$HERE/src" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" \
  --release 25 \
  -encoding UTF-8 \
  -Xlint:-options \
  -cp "$CP" \
  -d "$CLASSES" \
  @"$OUT/sources.txt"

cp "$HERE/src/fabric.mod.json" "$CLASSES/fabric.mod.json"

echo "Packaging $JAR"
"$JAVA" -version >/dev/null 2>&1
( cd "$CLASSES" && jar --create --file "$JAR" --main-class com.cigaro.bridge.CigaroBridgeClient . ) \
  2>/dev/null || ( cd "$CLASSES" && "$PRISM/java/java-runtime-epsilon/bin/jar" --create --file "$JAR" . )

echo "Built $JAR"
ls -la "$JAR"
