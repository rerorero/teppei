# -*- coding: utf-8 -*-
from fabric.api import *

@task
def do():
  package()
  network()
  reboot()

def package():
  # 基本
  run("yum -y install unzip")

  # java
  run("yum -y install java-1.7.0-openjdk")
  run("yum -y install java-1.7.0-openjdk-devel")

def network():
  run("sed -i \"s/^HOSTNAME\=.*$/HOSTNAME=%s/g\" /etc/sysconfig/network" % env.hosts )

def reboot():
  run("reboot")

