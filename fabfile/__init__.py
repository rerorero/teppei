# -*- coding: utf-8 -*-
from fabric.api import *
import setup
import app

# ssh
env.user = "root"
env.hosts = "tepetepe.tk"
env.key_filename = '~/.ssh/id_rsa'
env.password = ""

env.pwd = local("pwd", True)

# release info
env.app = "teppei"
env.snap_name = env.app + "-1.0-SNAPSHOT"
env.localsnap_path = env.pwd + "/target/universal/" + env.snap_name + ".zip"
env.delivered_dir = "/opt/" + env.app
env.deploy_dir = env.delivered_dir + "/" + env.snap_name
env.logpath = "/var/log/" + env.app + ".log"

