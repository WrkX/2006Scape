#!/usr/bin/env bash
#
# Idempotent Cloud Agent bootstrap for 2006Scape.
#
# 2006Scape is a Java 8 Maven multi-module project (client + server). The
# default Cloud Agent image ships a newer JDK and no Maven, so this script
# provisions Temurin JDK 8 and Apache Maven under /opt (only when missing),
# exposes them on PATH, then builds the project and prepares the server config.
#
# Safe to run repeatedly: every step checks for existing state before acting.
set -euo pipefail

JDK_LINK=/opt/java/jdk8
MVN_LINK=/opt/maven/current
MAVEN_VERSION=3.9.9

if command -v sudo >/dev/null 2>&1 && [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
else
  SUDO=""
fi

install_jdk8() {
  if [ -x "$JDK_LINK/bin/javac" ]; then
    return
  fi
  echo "==> Installing Temurin JDK 8"
  $SUDO mkdir -p /opt/java
  local tmp
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" \
    "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"
  $SUDO tar -xzf "$tmp" -C /opt/java
  rm -f "$tmp"
  local jdk_dir
  jdk_dir="$(find /opt/java -maxdepth 1 -type d -name 'jdk8u*' | sort | tail -n1)"
  $SUDO ln -sfn "$jdk_dir" "$JDK_LINK"
}

install_maven() {
  if [ -x "$MVN_LINK/bin/mvn" ]; then
    return
  fi
  echo "==> Installing Apache Maven ${MAVEN_VERSION}"
  $SUDO mkdir -p /opt/maven
  local tmp
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" \
    "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  $SUDO tar -xzf "$tmp" -C /opt/maven
  rm -f "$tmp"
  $SUDO ln -sfn "/opt/maven/apache-maven-${MAVEN_VERSION}" "$MVN_LINK"
}

expose_toolchain() {
  # Make Java 8 + Maven the default toolchain for login and non-login shells so
  # that terminals, IDE runners, and manual commands all resolve the pinned JDK.
  $SUDO tee /etc/profile.d/java8-maven.sh >/dev/null <<'PROFILE'
export JAVA_HOME=/opt/java/jdk8
export M2_HOME=/opt/maven/current
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH
PROFILE
  $SUDO ln -sfn "$JDK_LINK/bin/java" /usr/local/bin/java
  $SUDO ln -sfn "$JDK_LINK/bin/javac" /usr/local/bin/javac
  $SUDO ln -sfn "$MVN_LINK/bin/mvn" /usr/local/bin/mvn
}

install_jdk8
install_maven
expose_toolchain

export JAVA_HOME="$JDK_LINK"
export PATH="$JAVA_HOME/bin:$MVN_LINK/bin:$PATH"

echo "==> Toolchain"
java -version
mvn -version

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> Building 2006Scape (client + server)"
mvn -B clean install

# The server reads runtime settings from ServerConfig.json, which is gitignored
# (see .gitignore). Seed it from the tracked sample so the server can boot.
if [ ! -f "2006Scape Server/ServerConfig.json" ]; then
  echo "==> Creating 2006Scape Server/ServerConfig.json from sample"
  cp "2006Scape Server/ServerConfig.Sample.json" "2006Scape Server/ServerConfig.json"
fi

echo "==> Setup complete"
