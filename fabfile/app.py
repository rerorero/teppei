# -*- coding: utf-8 -*-
from fabric.api import *
from fabric.contrib.files import *

@task
def deploy():
  snap()
  deliver()
  start()

@task
def snap():
  local('play clean comppile dist')

@task
def deliver():
  if exists(env.delivered_dir):
    run("rm -rf " + env.delivered_dir)
  run("mkdir " + env.delivered_dir)

  zip_name = env.snap_name + ".zip"
  put(env.localsnap_path, env.delivered_dir + "/" + zip_name)
  with cd(env.delivered_dir):
    run("unzip " + zip_name)

@task
def start():
  stop()
  with cd(env.deploy_dir + "/bin"):
    run("nohup ./" + env.app + " -mem 512 -Dhttp.port=80 > %s" % env.logpath)

@task
def stop():
  with cd(env.deploy_dir):
    if exists("./RUNNING_PID"):
      run("kill -9 `cat ./RUNNING_PID`")
      run("rm ./RUNNING_PID")

  # process でもチェック
  check_cmd = "ps afx | grep 'play.core.server.NettyServer' | grep -v grep | awk '{print $1}'"
  if run(check_cmd, warn_only=True).succeeded:
    run(check_cmd + " | xargs kill -9")

@task
def log():
  run("tail -f " + env.logpath)

