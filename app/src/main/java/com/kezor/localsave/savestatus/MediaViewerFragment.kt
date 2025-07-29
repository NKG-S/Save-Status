@file:Suppress("DEPRECATION")
package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.kezor.localsave.savestatus.databinding.FragmentImageViewerBinding
import com.kezor.localsave.savestatus.databinding.FragmentVideoPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MediaViewerFragment : Fragment() {

    private val args: MediaViewerFragmentArgs by navArgs()
    private var imageBinding: FragmentImageViewerBinding? = null
    private var videoBinding: FragmentVideoPlayerBinding? = null
    private var player: ExoPlayer? = null
    private var isVideo: Boolean = false
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition = 0L
    private lateinit var mediaList: Array<MediaItem>
    private var currentIndex: Int = 0
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        videoBinding?.videoPlayerView?.hideController()
        videoBinding?.appBarLayout?.visibility = View.VISIBLE
        videoBinding?.actionBarContainer?.visibility = View.VISIBLE
    }
    private lateinit var gestureDetector: GestureDetector
    private var isFromSavedSection: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaList = args.mediaList
        currentIndex = args.currentIndex
        isFromSavedSection = arguments?.getBoolean("isFromSavedSection", false) == true
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (!isFromSavedSection) {
            val savedFolderPath = getSavedFolderPath()
            isFromSavedSection = mediaList.any { it.file.absolutePath.startsWith(savedFolderPath) }
        }

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        showPreviousMedia()
                    } else {
                        showNextMedia()
                    }
                    return true
                }
                return false
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val mediaItem = mediaList[currentIndex]
        isVideo = mediaItem.type == Constants.MEDIA_TYPE_VIDEO
        return if (isVideo) {
            videoBinding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
            videoBinding?.root ?: throw IllegalStateException("Video binding is null")
        } else {
            imageBinding = FragmentImageViewerBinding.inflate(inflater, container, false)
            imageBinding?.root ?: throw IllegalStateException("Image binding is null")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideSystemUI()
        updateMediaView(mediaList[currentIndex])
        view.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        // Check if media is already saved when opening
        checkIfMediaAlreadySaved()
    }

    @SuppressLint("UseKtx")
    private fun checkIfMediaAlreadySaved() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mediaItem = mediaList[currentIndex]
                val isSaved = if (isFromSavedSection) {
                    true
                } else {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val customUri = prefs.getString(Constants.KEY_SAVE_FOLDER_URI, null)

                    if (!customUri.isNullOrEmpty()) {
                        val folderDoc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(customUri))
                        folderDoc?.let { isMediaAlreadySavedInSAF(it, mediaItem) } ?: false
                    } else {
                        isMediaAlreadySaved(mediaItem)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (isSaved) {
                        hideSaveButton()
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error checking if media is saved", e)
            }
        }
    }

    private fun hideSaveButton() {
        videoBinding?.btnSave?.visibility = View.GONE
        imageBinding?.btnSave?.visibility = View.GONE
    }

    private fun updateMediaView(mediaItem: MediaItem) {
        if (player != null) {
            releasePlayer()
        }
        if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) {
            if (videoBinding == null) {
                videoBinding = FragmentVideoPlayerBinding.inflate(layoutInflater, view?.parent as? ViewGroup, false)
                (view?.parent as? ViewGroup)?.addView(videoBinding?.root)
            }
            videoBinding?.root?.visibility = View.VISIBLE
            imageBinding?.root?.visibility = View.GONE
            initializePlayer(mediaItem)
            setupVideoUI(mediaItem)
        } else {
            if (imageBinding == null) {
                imageBinding = FragmentImageViewerBinding.inflate(layoutInflater, view?.parent as? ViewGroup, false)
                (view?.parent as? ViewGroup)?.addView(imageBinding?.root)
            }
            imageBinding?.root?.visibility = View.VISIBLE
            videoBinding?.root?.visibility = View.GONE
            setupImageViewer(mediaItem)
            setupImageUI(mediaItem)
        }
    }

    private fun setupVideoUI(mediaItem: MediaItem) {
        videoBinding?.apply {
            topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }
            configureSaveButtonVisibility(btnSave, mediaItem)
            btnShare.visibility = View.VISIBLE
            btnSave.setOnClickListener { saveMedia(mediaList[currentIndex]) }
            btnShare.setOnClickListener { shareMedia(mediaList[currentIndex]) }

            videoPlayerView.setControllerVisibilityListener(object : StyledPlayerView.ControllerVisibilityListener {
                override fun onVisibilityChanged(visibility: Int) {
                    val controlsLayout = videoPlayerView.findViewById<LinearLayout>(R.id.controls_layout)
                    if (visibility == View.VISIBLE) {
                        appBarLayout.visibility = View.GONE
                        actionBarContainer.visibility = View.GONE
                        controlsLayout?.visibility = View.VISIBLE
                        controlsHandler.removeCallbacks(hideControlsRunnable)
                        controlsHandler.postDelayed(hideControlsRunnable, 1500)
                    } else {
                        appBarLayout.visibility = View.VISIBLE
                        actionBarContainer.visibility = View.VISIBLE
                        controlsLayout?.visibility = View.GONE
                    }
                }
            })
        }
    }

    private fun setupImageUI(mediaItem: MediaItem) {
        imageBinding?.apply {
            topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }
            configureSaveButtonVisibility(btnSave, mediaItem)
            btnShare.visibility = View.VISIBLE
            btnSave.setOnClickListener { saveMedia(mediaList[currentIndex]) }
            btnShare.setOnClickListener { shareMedia(mediaList[currentIndex]) }
        }
    }

    private fun configureSaveButtonVisibility(saveButton: View, mediaItem: MediaItem) {
        when {
            isFromSavedSection -> {
                saveButton.visibility = View.GONE
            }
            isMediaInSavedFolder(mediaItem) -> {
                saveButton.visibility = View.GONE
            }
            else -> {
                saveButton.visibility = View.VISIBLE
            }
        }
    }














































    private suspend fun saveMediaUsingSAF(mediaItem: MediaItem, folderUri: Uri, newFileName: String) {
        try {
            val resolver = requireContext().contentResolver
            val folderDoc = DocumentFile.fromTreeUri(requireContext(), folderUri)

            if (folderDoc == null || !folderDoc.exists()) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), "Cannot access save location")
                }
                return
            }

            // Check if file with same content already exists
            if (isMediaAlreadySavedInSAF(folderDoc, mediaItem)) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), "This media is already saved")
                    hideSaveButton()
                }
                return
            }

            // Create the new file
            val mimeType = when (mediaItem.type) {
                Constants.MEDIA_TYPE_IMAGE -> "image/${mediaItem.file.extension.lowercase()}"
                Constants.MEDIA_TYPE_VIDEO -> "video/${mediaItem.file.extension.lowercase()}"
                else -> "*/*"
            }

            val newFileDoc = folderDoc.createFile(mimeType, newFileName)
                ?: throw Exception("Failed to create file in selected location")

            // Copy content
            resolver.openInputStream(Uri.fromFile(mediaItem.file))?.use { inputStream ->
                resolver.openOutputStream(newFileDoc.uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            withContext(Dispatchers.Main) {
                Utils.showToast(requireContext(), "Media saved successfully")
                Utils.scanMediaFile(requireContext(), newFileDoc.uri.toString())
                hideSaveButton()
            }
        } catch (e: Exception) {
            throw Exception("SAF save failed: ${e.message}")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private suspend fun saveMediaUsingFile(mediaItem: MediaItem, newFileName: String) {
        try {
            val saveDir = File(getSavedFolderPath())
            if (!saveDir.exists()) saveDir.mkdirs()

            // Check if file with same content already exists
            if (isMediaAlreadySaved(mediaItem, saveDir)) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), "This media is already saved")
                    hideSaveButton()
                }
                return
            }

            val destFile = File(saveDir, newFileName)

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveMediaUsingMediaStore(mediaItem, newFileName)
            } else {
                Utils.copyFile(mediaItem.file, destFile)
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Utils.showToast(requireContext(), "Media saved successfully")
                    Utils.scanMediaFile(requireContext(), destFile.absolutePath)
                    hideSaveButton()
                } else {
                    Utils.showToast(requireContext(), "Failed to save media")
                }
            }
        } catch (e: Exception) {
            throw Exception("File save failed: ${e.message}")
        }
    }


    private fun showNextMedia() {
        if (currentIndex < mediaList.size - 1) {
            currentIndex++
            updateMediaView(mediaList[currentIndex])
        } else {
            Toast.makeText(requireContext(), "No more media to the right", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPreviousMedia() {
        if (currentIndex > 0) {
            currentIndex--
            updateMediaView(mediaList[currentIndex])
        } else {
            Toast.makeText(requireContext(), "No more media to the left", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isMediaInSavedFolder(mediaItem: MediaItem): Boolean {
        val savedFolderPath = getSavedFolderPath()
        val mediaPath = mediaItem.file.absolutePath
        return mediaPath.startsWith(savedFolderPath)
    }

    private fun setupImageViewer(mediaItem: MediaItem) {
        imageBinding?.imageFullScreen?.let { imageView ->
            Glide.with(this).load(mediaItem.file).into(imageView)
        }
    }

    private fun initializePlayer(mediaItem: MediaItem) {
        if (player != null) {
            releasePlayer()
        }
        try {
            player = ExoPlayer.Builder(requireContext()).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build().also { exoPlayer ->
                videoBinding?.videoPlayerView?.player = exoPlayer
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentWindow, playbackPosition)
                val exoMediaItem = ExoMediaItem.fromUri(Uri.fromFile(mediaItem.file))
                exoPlayer.setMediaItem(exoMediaItem)
                exoPlayer.prepare()
                setupControlButtons(exoPlayer)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Failed to play video: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        updatePlayPauseButtonVisibility(exoPlayer.playWhenReady, state)
                        when (state) {
                            Player.STATE_BUFFERING, Player.STATE_READY -> showControlsTemporarily()
                            Player.STATE_ENDED -> {
                                exoPlayer.seekTo(0)
                                exoPlayer.playWhenReady = false
                                updatePlayPauseButtonVisibility(false, Player.STATE_ENDED)
                                showControlsTemporarily()
                            }
                            Player.STATE_IDLE -> {
                                updatePlayPauseButtonVisibility(false, Player.STATE_IDLE)
                                showControlsTemporarily()
                            }
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        updatePlayPauseButtonVisibility(playWhenReady, exoPlayer.playbackState)
                    }
                })
                updatePlayPauseButtonVisibility(exoPlayer.playWhenReady, exoPlayer.playbackState)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Video player initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("UseKtx")
    private fun saveMedia(mediaItem: MediaItem) {
        if (isFromSavedSection) {
            Toast.makeText(requireContext(), "Media is already saved", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val customUri = prefs.getString(Constants.KEY_SAVE_FOLDER_URI, null)

                // Generate unique ID for the file
                val fileId = UUID.randomUUID().toString().substring(0, 8)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val extension = mediaItem.file.extension
                val newFileName = "${fileId}_${mediaItem.file.nameWithoutExtension}_$timestamp.$extension"

                if (!customUri.isNullOrEmpty()) {
                    // Handle SAF URI
                    saveMediaUsingSAF(mediaItem, Uri.parse(customUri), newFileName)
                } else {
                    // Handle regular file path
                    saveMediaUsingFile(mediaItem, newFileName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), "Save failed: ${e.message}")
                    Log.e("MediaViewFragment", "Save failed", e)
                }
            }
        }
    }

    @SuppressLint("Recycle")
    fun saveMediaUsingMediaStore(mediaItem: MediaItem, fileName: String): Boolean {
        return try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, when (mediaItem.type) {
                    Constants.MEDIA_TYPE_IMAGE -> "image/${mediaItem.file.extension.lowercase()}"
                    Constants.MEDIA_TYPE_VIDEO -> "video/${mediaItem.file.extension.lowercase()}"
                    else -> "*/*"
                })
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS +
                        File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
            }

            val uri = resolver.insert(
                when (mediaItem.type) {
                    Constants.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    Constants.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                },
                contentValues
            ) ?: return false

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(mediaItem.file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isMediaAlreadySavedInSAF(folderDoc: DocumentFile, mediaItem: MediaItem): Boolean {
        // Compare file content hashes to detect duplicates
        val sourceHash = Utils.calculateFileHash(mediaItem.file)

        return folderDoc.listFiles().any { file ->
            try {
                val tempFile = File.createTempFile("temp", null, requireContext().cacheDir)
                requireContext().contentResolver.openInputStream(file.uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val targetHash = Utils.calculateFileHash(tempFile)
                tempFile.delete()
                sourceHash == targetHash
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun isMediaAlreadySaved(mediaItem: MediaItem, dir: File? = null): Boolean {
        val saveDir = dir ?: File(getSavedFolderPath())
        if (!saveDir.exists()) return false

        // Compare file content hashes to detect duplicates
        val sourceHash = Utils.calculateFileHash(mediaItem.file)

        return saveDir.listFiles()?.any { file ->
            val targetHash = Utils.calculateFileHash(file)
            sourceHash == targetHash
        } == true
    }

    private fun setupControlButtons(exoPlayer: ExoPlayer) {
        videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_play)?.setOnClickListener {
            exoPlayer.play()
            showControlsTemporarily()
        }
        videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_pause)?.setOnClickListener {
            exoPlayer.pause()
            showControlsTemporarily()
        }
        videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_rew)?.setOnClickListener {
            exoPlayer.seekBack()
            showControlsTemporarily()
        }
        videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_ffwd)?.setOnClickListener {
            exoPlayer.seekForward()
            showControlsTemporarily()
        }
    }

    private fun updatePlayPauseButtonVisibility(playWhenReady: Boolean, playbackState: Int) {
        val playButton = videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_play)
        val pauseButton = videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_pause)
        if (playButton != null && pauseButton != null) {
            if (playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
                playButton.visibility = View.GONE
                pauseButton.visibility = View.VISIBLE
            } else {
                playButton.visibility = View.VISIBLE
                pauseButton.visibility = View.GONE
            }
        }
    }

    private fun showControlsTemporarily() {
        videoBinding?.videoPlayerView?.showController()
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlsHandler.postDelayed(hideControlsRunnable, 1500)
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.stop()
            exoPlayer.release()
        }
        player = null
        controlsHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun shareMedia(mediaItem: MediaItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val cacheDir = requireContext().cacheDir
                tempFile = File(cacheDir, "share_temp_${mediaItem.file.name}")

                val success = Utils.copyFile(mediaItem.file, tempFile)

                if (!success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to prepare file for sharing.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    tempFile
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        type = when (mediaItem.type) {
                            Constants.MEDIA_TYPE_IMAGE -> "image/*"
                            Constants.MEDIA_TYPE_VIDEO -> "video/*"
                            else -> "*/*"
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(shareIntent, getString(R.string.saved_share_button))
                    startActivity(chooser)

                    controlsHandler.postDelayed({
                        tempFile.delete()
                    }, 10000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val customUri = prefs.getString(Constants.KEY_SAVE_FOLDER_URI, null)

        return if (!customUri.isNullOrEmpty()) {
            // For SAF URI, we need to get the actual path
            try {
                val uri = Uri.parse(customUri)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                docFile?.uri?.path ?: (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
            } catch (e: Exception) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
            }
        } else {
            // Default path
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
        }
    }

    private fun hideSystemUI() {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun showSystemUI() {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        if (isVideo && player == null) {
            initializePlayer(mediaList[currentIndex])
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        player?.playWhenReady = playWhenReady
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showSystemUI()
        if (isVideo) {
            releasePlayer()
            videoBinding?.videoPlayerView?.player = null
            videoBinding = null
        } else {
            imageBinding = null
        }
        requireContext().cacheDir.listFiles { file ->
            file.name.startsWith("share_temp_")
        }?.forEach { it.delete() }
    }
}