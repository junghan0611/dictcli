{
  description = "dictcli — 힣의 어휘 연결체. EDN 트리플 그래프 CLI (GraalVM native)";

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
          # 기본: GraalVM (native-image 빌드)
          default = pkgs.mkShell {
            name = "dictcli";

            buildInputs = with pkgs; [
              clojure
              graalvm       # JDK + native-image
            ];

            JAVA_HOME = graalvm;
            GRAALVM_HOME = graalvm;

            shellHook = ''
              echo "dictcli dev shell (GraalVM $(native-image --version 2>/dev/null | head -1))"
              echo "  ./run.sh native-build  — GraalVM native binary"
              echo "  ./run.sh expand 보편   — 쿼리 확장"
              echo "  ./run.sh test          — 테스트"
            '';
          };

          # JVM만 (가벼운 개발용)
          jvm = pkgs.mkShell {
            name = "dictcli-jvm";

            buildInputs = with pkgs; [
              clojure
              jdk17_headless
            ];

            shellHook = ''
              echo "dictcli dev shell (JVM only — native-image 불가)"
            '';
          };
        };
      });
}
