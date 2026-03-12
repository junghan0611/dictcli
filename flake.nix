{
  description = "dictcli — 개인 어휘 사전 CLI. ten 형식 glossary → SQLite 인덱스";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        graalvm = pkgs.graalvmPackages.graalvm-ce;
      in
      {
        devShells = {
          # 기본: GraalVM (native-image 포함)
          default = pkgs.mkShell {
            name = "dictcli";

            buildInputs = with pkgs; [
              clojure
              graalvm       # JDK + native-image
              sqlite
            ];

            JAVA_HOME = graalvm;
            GRAALVM_HOME = graalvm;

            shellHook = ''
              echo "dictcli dev shell (GraalVM)"
              echo "  ./run.sh build        — glossary → SQLite"
              echo "  ./run.sh native-build — GraalVM native binary"
              echo "  ./run.sh lookup 존재  — 용어 검색"
            '';
          };

          # JVM만 (가벼운 개발용)
          jvm = pkgs.mkShell {
            name = "dictcli-jvm";

            buildInputs = with pkgs; [
              clojure
              jdk17_headless
              sqlite
            ];

            shellHook = ''
              echo "dictcli dev shell (JVM only)"
            '';
          };
        };
      });
}
