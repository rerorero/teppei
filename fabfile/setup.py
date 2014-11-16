# -*- coding: utf-8 -*-
from fabric.api import *

@task
def do():
  package()
  filesystem()
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

def filesystem():
  if exists(env.image_dir):
    run("mkdir -p " + env.image_dir)

def reboot():
  run("reboot")

