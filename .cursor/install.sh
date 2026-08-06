#!/usr/bin/env bash
#
# Idempotent Cloud Agent bootstrap for SingleScape (2006Scape).
#
# The project builds TypeScript content with pnpm and the Java engine with Maven.
# The default Cloud Agent image may not ship the pinned toolchain, so this script
# provisions Temurin JDK 17, Apache Maven, Node.js 20, and pnpm under /opt
# (only when missing), exposes them on PATH, then runs the root build.
#
# Safe to run repeatedly: every step checks for existing state before acting.
set -euo pipefail

JDK_LINK=/opt/java/jdk17
MVN_LINK=/opt/maven/current
NODE_LINK=/opt/node/current
MAVEN_VERSION=3.9.9
NODE_VERSION=20.18.1

if command -v sudo >/dev/null 2>&1 && [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
else
  SUDO=""
fi

install_jdk17() {
  if [ -x "$JDK_LINK/bin/javac" ]; then
    return
  fi
  echo "==> Installing Temurin JDK 17"
  $SUDO mkdir -p /opt/java
  local tmp
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  $SUDO tar -xzf "$tmp" -C /opt/java
  rm -f "$tmp"
  local jdk_dir
  jdk_dir="$(find /opt/java -maxdepth 1 -type d -name 'jdk-17*' | sort | tail -n1)"
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

install_node() {
  if [ -x "$NODE_LINK/bin/node" ]; then
    return
  fi
  echo "==> Installing Node.js ${NODE_VERSION}"
  $SUDO mkdir -p /opt/node
  local tmp
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" \
    "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz"
  $SUDO tar -xJf "$tmp" -C /opt/node
  rm -f "$tmp"
  $SUDO ln -sfn "/opt/node/node-v${NODE_VERSION}-linux-x64" "$NODE_LINK"
}

install_pnpm() {
  if command -v pnpm >/dev/null 2>&1; then
    return
  fi
  echo "==> Installing pnpm"
  "$NODE_LINK/bin/corepack" enable
  "$NODE_LINK/bin/corepack" prepare pnpm@11.13.1 --activate
}

expose_toolchain() {
  $SUDO tee /etc/profile.d/singlescape-toolchain.sh >/dev/null <<'PROFILE'
export JAVA_HOME=/opt/java/jdk17
export M2_HOME=/opt/maven/current
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:/opt/node/current/bin:$PATH
PROFILE
  $SUDO ln -sfn "$JDK_LINK/bin/java" /usr/local/bin/java
  $SUDO ln -sfn "$JDK_LINK/bin/javac" /usr/local/bin/javac
  $SUDO ln -sfn "$MVN_LINK/bin/mvn" /usr/local/bin/mvn
  $SUDO ln -sfn "$NODE_LINK/bin/node" /usr/local/bin/node
  $SUDO ln -sfn "$NODE_LINK/bin/npm" /usr/local/bin/npm
  $SUDO ln -sfn "$NODE_LINK/bin/corepack" /usr/local/bin/corepack
}

install_jdk17
install_maven
install_node
expose_toolchain
install_pnpm

export JAVA_HOME="$JDK_LINK"
export PATH="$JAVA_HOME/bin:$MVN_LINK/bin:$NODE_LINK/bin:$PATH"

echo "==> Toolchain"
java -version
mvn -version
node --version
pnpm --version

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> Building SingleScape (content + engine)"
bash scripts/build.sh

echo "==> Setup complete"
