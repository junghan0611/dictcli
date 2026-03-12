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
      in
      {
        devShells.default = pkgs.mkShell {
          name = "dictcli";

          buildInputs = with pkgs; [
            # Clojure
            clojure
            jdk17_headless

            # SQLite
            sqlite

            # etags (ten 호환)
            emacs-nox
          ];

          shellHook = ''
            echo "dictcli dev shell"
            echo "  clj -M:run build   — glossary → SQLite"
            echo "  clj -M:run lookup  — 용어 검색"
          '';
        };
      });
}
