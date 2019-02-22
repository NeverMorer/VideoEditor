## VideoEditor
A library for edit video without JNI base on **MediaCodec** farmework

####Feature:
* Compress video with specific bitrate
* Scale video with specific width/height
* Trim video with specific start/end time
* High performance

#### HowToUse

``` kotlin
  coder = VideoAudioCoder(videoPath, destPath)
  coder.setWithAudio(false)

  val isWidthBig = mediaInfo.getWidth() > mediaInfo.getHeight()
  val scaleWidth = if (isWidthBig) 720 else 480
  val scaleHeight = if (isWidthBig) 480 else 720

  coder.withScale(scaleWidth, scaleHeight)
  coder.withTrim(3000, 6000)
  coder.withRotateFrame()

  coder.setCallback(object : VideoAudioCoder.ResultCallback {
      override fun onSucceed() {
         
      }
      override fun onFailed(errorMessage: String) {
                  
      }, 
      Handler(Looper.getMainLooper()))

  coder.startAsync()

```