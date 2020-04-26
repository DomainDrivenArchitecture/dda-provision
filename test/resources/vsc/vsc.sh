# install_code
curl https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor > microsoft.gpg
sudo install -o root -g root -m 644 microsoft.gpg /etc/apt/trusted.gpg.d/
sudo sh -c 'echo "deb [arch=amd64] https://packages.microsoft.com/repos/vscode stable main" > /etc/apt/sources.list.d/vscode.list'
sudo apt-get update
sudo apt-get -y install code


## install_VSC_extensions
#
##clojure
#code --install-extension betterthantomorrow.calva
#code --install-extension martinklepsch.clojure-joker-linter
#code --install-extension DavidAnson.vscode-markdownlint
#curl -Lo joker-0.12.2-linux-amd64.zip https://github.com/candid82/joker/releases/download/v0.12.2/joker-0.12.2-linux-amd64.zip
#unzip joker-0.12.2-linux-amd64.zip
#sudo mv joker /usr/local/bin/
#
## python
#code --install-extension ms-python.python

