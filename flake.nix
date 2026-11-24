{
  description = "Local Java 25 development shell for PierdolPlaytime";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
    in
    {
      devShells = nixpkgs.lib.genAttrs systems (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [ jdk25 bash coreutils findutils gnused ];

            JAVA_HOME = pkgs.jdk25.home;
            JDK25_HOME = pkgs.jdk25.home;
            ORG_GRADLE_JAVA_INSTALLATIONS_FROM_ENV = "JDK25_HOME";
          };
        });
    };
}
