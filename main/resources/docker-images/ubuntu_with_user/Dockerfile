FROM ubuntu:18.04

RUN apt-get update && apt-get -y install sudo
RUN useradd -m testuser && echo "testuser:testuser" | chpasswd && adduser testuser sudo
RUN echo "testuser ALL=(ALL:ALL) NOPASSWD: ALL" | sudo tee /etc/sudoers.d/testuser

# avoid error installing vsc estensions (error: error :while loading shared libraries: libX11-xcb.so.1: cannot open shared object file: No such file or directory)
# they should be available on regular ubuntu
#RUN apt-get install --assume-yes apt-utils libx11-xcb1 libasound2 x11-apps libice6 libsm6 libxaw7 libxft2 libxmu6 libxpm4 libxt6 x11-apps xbitmaps

USER testuser
CMD /bin/bash
WORKDIR /home/testuser
