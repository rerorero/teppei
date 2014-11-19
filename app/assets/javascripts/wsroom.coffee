#----------------------------------------------------
# vendor prefix
#----------------------------------------------------
window.URL = window.URL || window.webkitURL

navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia

#----------------------------------------------------
# error
#----------------------------------------------------
class Error
  # constructor
  constructor:(@message)->

#----------------------------------------------------
# Base view
#----------------------------------------------------
class CamView
  camView: null
  # @todo auto modified
  capQuality: 0.3

  constructor:(viewElement)->
    @camView = viewElement

  base64toBlob:(base64Image)->
    tmp = base64Image.split(',')
    data = atob(tmp[1])
    mime = tmp[0].split(':')[1].split(';')[0]
    arr = new Uint8Array(data.length)
    for i in [0..data.length-1]
      arr[i] = data.charCodeAt(i)
    new Blob([arr], { type: mime })

#----------------------------------------------------
# Capture view
#----------------------------------------------------
class CaptureCam extends CamView
  READYSTATE_COMPLETED = 4
  HTTP_STATUS_OK = 200
  IMAGE_TYPE = 'image/jpeg'

  videoobj:null
  onShooting:false
  localStream:null
  capture : {
    count:0
    started:null
    enabled:false
    duration:2000
    echoPeriod:1
  }

  constructor:(viewElement)->
    super(viewElement)

  getCaptureBase64:(quality)->
    if !@videoobj? || @videoobj.videoWidth is 0
      console.log 'not playing'
      return null
    canvas = document.createElement('canvas')
    canvas.width = @videoobj.videoWidth * quality
    canvas.height = @videoobj.videoHeight * quality
    context = canvas.getContext('2d')
    context.drawImage(@camView, 
      0, 
      0,
      @videoobj.videoWidth,
      @videoobj.videoHeight,
      0, 
      0, 
      canvas.width, 
      canvas.height)
    canvas.toDataURL(IMAGE_TYPE)

  onCpatureTimer:->
    if @capture.enabled
      setTimeout(()=> 
        @onCpatureTimer()
      , @capture.duration)
    data = @getCaptureBase64(@capQuality)
    return if data is null
    @capture.count++
    reqecho = false
    if ((@capture.count % @capture.echoPeriod) == 0)
      reqecho = true
      @capture.started = (new Date()).getTime()
      @capture.echoPeriod += @capture.echoPeriod
    # send stream data to server 
    g_socket.send(JSON.stringify({
      kind: 'capture'
      echo: reqecho
      data: data
    }))

  onCapEcho:->
    if @capture.started?
      finished = (new Date()).getTime()
      roundtrip =  finished - @capture.started
      console.log 'roundtrip='+roundtrip

  startCapture:->
    navigator.getUserMedia({video: true, audio: false, toString: ()-> "video, audio" },
      (stream)=>
        @camView.autoplay = true
        @camView.src = window.URL.createObjectURL(stream)
        @localStream = stream
        @onStreamStart()

        that = this
        @camView.addEventListener 'playing', ()->
          console.log('playing! w=' + this.videoWidth)
          that.videoobj = this
        , false
      ,
      (error)=>
        @stopCapture
        throw new Error(error)
    )
    null

  # onStreamStart:->
  #   @capture.enabled = true
  #   setTimeout(()=> 
  #     @onCpatureTimer()
  #   , @capture.duration)

  stopCapture:->
    @capture.enabled = false
  
  takePhoto:(postUrl, callback)->
    console.log 'add image url:'+postUrl
    data64 = @getCaptureBase64(1)
    return if data64 is null
    blobimg = @base64toBlob(data64)
    forms = new FormData()
    forms.append("image", blobimg);
    req = new XMLHttpRequest()
    req.onreadystatechange = =>
      @onShooting = false
      if this.readyState is READYSTATE_COMPLETED
        callback(JSON.parse(this.responseText))
    req.open('POST', postUrl, true)
    req.send(forms)
    @onShooting = true

#----------------------------------------------------
# for WebRTC
#----------------------------------------------------
class CaptureCamRTC extends CaptureCam
  peer:null
  call:null
  receiver:null
  usesRTC:true

  constructor:(viewElement, @peerid)->
    super(viewElement)
    @peer = new Peer(@uidToPeerId(g_user), {key: 'k7is0ssil3sg7gb9', debug: 3}); 
    @peer.on 'open', ->
      console.log 'RTC open succeed:'
    @peer.on 'close', =>
      console.log 'RTC closed:'
      #@stopCall()

  uidToPeerId:(uid)-> 'teppe_'+uid

  onStreamStart:->
    # start call
    @callTo(null, @receiver.userId) if @receiver?

  setReadyToCall:(destRec)->
    @receiver = destRec

  waitCall:(destRec)->
    @receiver = destRec
    @peer.on 'call', (remoteCall)=>
      console.log('Anser for remote')
      if !@localStream?
        console.log('No streams')
        return
      remoteCall.answer @localStream
      @callTo(remoteCall)

  callTo:(newCall, destId)->
    if !@localStream? or !@peer?
      console.log('Invalid status for RTC')
      return
    # stop if call exists already
    @stopCall() if @call?

    if !newCall? and destId?
      console.log('attempt to make RTC calling established')
      newCall = @peer.call(@uidToPeerId(destId), @localStream)
      console.log newCall
    return if !newCall?

    newCall.on 'stream', (remoteStream)=>
      console.log('Calling from remote')
      if @receiver?
        source = window.URL.createObjectURL(remoteStream)
        @receiver.setSrc(source)

  stopCall:(destroy = false)->
    console.log "stop calling."
    if @call?
      console.log "call closing."
      @call.close()
      @call = null
    @peer.destroy() if destroy
    @receiver = null

#----------------------------------------------------
# Receiver view
#----------------------------------------------------
class ReveiverCam extends CamView
  userId:0
  constructor:(viewElement, uid)->
    super(viewElement)
    @userId = uid

  setSrc:(data)->
    @camView.autoplay = true
    @camView.src = data
    console.log("reciever source sets!")
    console.log data

g_capture = null
g_receiver = null
g_socket = null
g_user = null
#----------------------------------------------------
# on ready
#----------------------------------------------------
$ ->
  # @todo すでに接続済みならnot null もしくはweb socket 接続時に確認しその後joined
  remote_user = $('#partner').attr('value')
  g_user = $('#whoami').attr('value')

  $('#btn-capture-stop').click ->
    g_capture.stopCapture()

  #---------------------------------
  # web socket 
  #---------------------------------
  if remote_user
    g_receiver = new ReveiverCam($('#video-receiver')[0], remote_user)

  g_capture = new CaptureCamRTC($('#video-capture')[0], g_user)
  # start call if other exists
  if g_receiver?
    g_capture.setReadyToCall(g_receiver)

  # commit to take pic function
  commitPic = (user) ->
    console.log 'take pic from ' + user 
    g_socket.send(JSON.stringify({
      kind: 'commitPic'
      from: user
    }))
    # g_ui.setUserPicState(uid, true, 0)

  takeMyPic = ->
    return if g_capture.onShooting
    url = jsRoutes.controllers.Application.addImage().url
    g_capture.takePhoto url, (result)->
      console.log 'take pic result: ' + result

  if g_socket?
    console.log 'socket has already opend'
  else
    wspath = jsRoutes.controllers.Websocket.stream(g_user).webSocketURL()
    if window['MozWebSocket']?
      g_socket = new MozWebSocket(wspath)
    else
      g_socket = new WebSocket(wspath)

    g_socket.onopen = ()-> 
      console.log 'opend'
      try 
        g_capture.startCapture()
      catch error
        alert error.message

    g_socket.onmessage = (e)-> 
      msg = JSON.parse(e.data)
      switch msg.kind
        #-----------------------------------------------
        # receive capture data
        #-----------------------------------------------
        # when "capture"
        #   console.log('[RX] capture')
        #   for receiver in g_receiver
        #     if receiver.userId is msg.user
        #       receiver.setSrc(msg.data)
        #       break
        #   true

        #-----------------------------------------------
        # user joined
        #-----------------------------------------------
        when "joined"
          console.log('[RX] joined : ' + msg.user)
          # add receiver
          g_receiver = new ReveiverCam($('#video-receiver')[0], msg.user)
          # wait webRTC call from new commer 
          g_capture.waitCall(g_receiver)

        #-----------------------------------------------
        # user left
        #-----------------------------------------------
        when "left"
          console.log('[RX] left :')
          # stop webRTC
          g_capture.stopCall()
          # remove receiver
          g_receiver = null

        #-----------------------------------------------
        # to take a photo
        #-----------------------------------------------
        when "commitPic"
          console.log('[RX] commitPic :' + msg)
          takeMyPic()

        #-----------------------------------------------
        # photo added
        #-----------------------------------------------
        # when "picAdded"
        #   console.log('[RX] added :' + msg)
        #   # g_ui.addUserPicture(msg.pic)
        #   # g_ui.setUserPicState(msg.pic.owner, false, msg.picnum)

        #-----------------------------------------------
        # photo added
        #-----------------------------------------------
        # when "picRemoved"
        #   console.log('[RX] removed :' + msg)
        #   # g_ui.removeUserPicture(msg.pic)
        #   # g_ui.setUserPicState(msg.pic.owner, false, msg.picnum)

        when "capEcho"
          g_capture.onCapEcho()

        when "error"
          console.error(msg.msg)

        when "kick"
          console.log("kicked!! "+msg.user)
          console.log(g_user)
          if msg.user == g_user
            g_socket.close(4500, 'onUnload') if g_socket?
            g_capture.stopCall(true) if g_capture?
            console.log("i had been kicked..")
          else
            console.log("receiver stop")
            if g_capture?
              g_capture.stopCall()
              g_receiver = null

        else
          console.error('[RX] unknown :' + e.data)

    g_socket.onclose = ()-> 
      console.log 'socket closed'
      # @todo notify to the capture and receivers
      g_socket = null

  window.onunload = ->
    if g_socket?
      g_socket.close(4500, 'onUnload')
    if g_capture.usesRTC?
      g_capture.stopCall

  #-----------------------
  # camera button
  #-----------------------
  $('#takepic').on 'click', ->
    button = $(this)
    button.attr('disabled', false)
    commitPic(g_user)
    # uid = button.attr('value')
    # rid = room_id.attr('value')
    # if uid is g_user.getId()
    #   return if g_capture.onShooting
    #   # g_ui.setUserPicState(uid, true, 0)
    #   button.attr('disabled', true)
    #   url = jsRoutes.controllers.Api.addPicture(rid, uid).url
    #   g_capture.takePhoto url, (result)->
    #     button.attr('disabled', false)
    # else
      # g_ui.setUserPicState(uid, true, 0)

  # $('#shoot-all').click ->
  #   takeMyPic()
  #   if !g_capture.onShooting 
  #     # g_ui.setUserPicState(uid, true, 0)
  #
  #   for receiver in g_receiver
  #     commitPic(receiver.userId)

  #-----------------------
  # picture li
  #-----------------------
  # $('.del-pic').live 'click', ->
  #   button = $(this)
  #   # remove element first
  #   lielem = button.closest('.pic-li')
  #   picid = lielem.attr('value')
  #   # g_ui.removeElement(lielem)
  #   jsRoutes.controllers.Api.deletePicture(picid).ajax( {
  #     success: (result) ->
  #       console.log result
  #     error: ()->
  #       console.log 'failed to delete picture : '+pid
  #     })
  #
