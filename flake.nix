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

        # FHS 환경 — native-image 바이너리가 표준 경로에 링크됨 (Docker 호환)
        fhsEnv = pkgs.buildFHSEnv {
          name = "dictcli-build";
          targetPkgs = pkgs: with pkgs; [
            clojure
            graalvm
            zlib
            glibc
            glibc.static
          ];
          runScript = pkgs.writeShellScript "dictcli-build-init" ''
            export JAVA_HOME=${graalvm}
            export GRAALVM_HOME=${graalvm}
            exec bash "$@"
          '';
        };
      in
      {
        packages.fhs = fhsEnv;

        devShells = {
          # 기본: GraalVM (native-image 빌드, FHS 표준 경로)
          default = pkgs.mkShell {
            name = "dictcli";
            buildInputs = with pkgs; [ clojure graalvm ];
            JAVA_HOME = graalvm;
            GRAALVM_HOME = graalvm;
            shellHook = ''
              echo "dictcli dev shell (GraalVM $(native-image --version 2>/dev/null | head -1))"
              echo "  ./run.sh build             — native binary (FHS, Docker 호환)"
              echo "  ./run.sh expand 보편       — 쿼리 확장"
              echo "  ./run.sh test              — 테스트"
            '';
          };

          # JVM만 (가벼운 개발용)
          jvm = pkgs.mkShell {
            name = "dictcli-jvm";
            buildInputs = with pkgs; [ clojure jdk17_headless ];
            shellHook = ''
              echo "dictcli dev shell (JVM only)"
            '';
          };
        };
      });
}
