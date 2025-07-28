@file:Suppress("DEPRECATION")
package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Cache the isFromSavedSection value to avoid multiple bundle lookups
    private var isFromSavedSection: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaList = args.mediaList
        currentIndex = args.currentIndex

        // Determine if this media is being viewed from the saved section
        // Check multiple sources to ensure we get the correct value
        isFromSavedSection = arguments?.getBoolean("isFromSavedSection", false) ?: false

        // Additional check: if the media path contains the saved folder path, assume it's from saved section
        if (!isFromSavedSection) {
            val savedFolderPath = getSavedFolderPath()
            isFromSavedSection = mediaList.any { it.file.absolutePath.startsWith(savedFolderPath) }
        }

        Log.d("MediaViewer", "isFromSavedSection: $isFromSavedSection")

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

    private fun setupVideoUI(mediaItem: MediaItem) {
        videoBinding?.apply {
            topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }

            // Determine save button visibility
            configureSaveButtonVisibility(btnSave, mediaItem)

            // Share button is always visible
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

            // Determine save button visibility
            configureSaveButtonVisibility(btnSave, mediaItem)

            // Share button is always visible
            btnShare.visibility = View.VISIBLE
            btnSave.setOnClickListener { saveMedia(mediaList[currentIndex]) }
            btnShare.setOnClickListener { shareMedia(mediaList[currentIndex]) }
        }
    }

    /**
     * Configures the save button visibility based on multiple criteria:
     * 1. If viewing from saved section - always hide
     * 2. If media file is already in saved folder - hide
     * 3. If media is already saved (by name check) - hide
     * 4. Otherwise - show
     */
    private fun configureSaveButtonVisibility(saveButton: View, mediaItem: MediaItem) {
        when {
            // Priority 1: If viewing from saved section, always hide save button
            isFromSavedSection -> {
                saveButton.visibility = View.GONE
                Log.d("MediaViewer", "Save button hidden: viewing from saved section")
            }

            // Priority 2: If the media file is already in the saved folder, hide save button
            isMediaInSavedFolder(mediaItem) -> {
                saveButton.visibility = View.GONE
                Log.d("MediaViewer", "Save button hidden: media already in saved folder")
            }

            // Priority 3: If media is already saved (by name matching), hide save button
            isMediaAlreadySaved(mediaItem) -> {
                saveButton.visibility = View.GONE
                Log.d("MediaViewer", "Save button hidden: media already saved")
            }

            // Default: Show save button
            else -> {
                saveButton.visibility = View.VISIBLE
                Log.d("MediaViewer", "Save button visible: media can be saved")
            }
        }
    }

    /**
     * Checks if the media file is already located in the saved folder
     */
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
                        Log.e("MediaViewer", "Player error: ${error.message}")
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
            Log.e("MediaViewer", "Player initialization failed", e)
            Toast.makeText(requireContext(), "Video player initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

    /**
     * Saves the given media item to a designated folder with a unique name.
     */
    private fun saveMedia(mediaItem: MediaItem) {
        // Extra safety check - don't save if from saved section
        if (isFromSavedSection) {
            Toast.makeText(requireContext(), "Media is already saved", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Ensure the correct save directory is used based on Constants.APP_SAVE_SUBDIRECTORY_NAME
                val saveDir = File(getSavedFolderPath())
                if (!saveDir.exists()) saveDir.mkdirs()

                if (isMediaAlreadySaved(mediaItem, saveDir)) {
                    withContext(Dispatchers.Main) {
                        Utils.showToast(requireContext(), getString(R.string.status_already_saved))
                        videoBinding?.btnSave?.visibility = View.GONE
                        imageBinding?.btnSave?.visibility = View.GONE
                    }
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val extension = mediaItem.file.extension
                val newFileName = "${mediaItem.file.nameWithoutExtension}_$timestamp.$extension"
                val destFile = File(saveDir, newFileName)

                val success = Utils.copyFile(mediaItem.file, destFile)

                withContext(Dispatchers.Main) {
                    if (success) {
                        Utils.showToast(requireContext(), getString(R.string.status_saved_successfully))
                        Utils.scanMediaFile(requireContext(), destFile.absolutePath)
                        // After saving, hide the save button as it's now saved
                        videoBinding?.btnSave?.visibility = View.GONE
                        imageBinding?.btnSave?.visibility = View.GONE
                    } else {
                        Utils.showToast(requireContext(), getString(R.string.status_save_failed))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), "Save failed: ${e.message}")
                }
                Log.e("MediaViewer", "Save error: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if a media item (by its original name) is already saved.
     * This is a simple check; a more robust check might involve content hashing or a database.
     */
    private fun isMediaAlreadySaved(mediaItem: MediaItem, dir: File? = null): Boolean {
        // Use the consistent getSavedFolderPath() here too
        val saveDir = dir ?: File(getSavedFolderPath())
        if (!saveDir.exists()) return false

        val baseId = mediaItem.file.nameWithoutExtension
        val extension = mediaItem.file.extension.lowercase()

        return saveDir.listFiles()?.any { file ->
            // Check if the file name (before the timestamp part) matches the original
            // and the extension matches.
            file.nameWithoutExtension.substringBeforeLast("_", file.nameWithoutExtension) == baseId &&
                    file.extension.equals(extension, ignoreCase = true)
        } == true
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

                    // Grant temporary read permission to all resolved activities
                    val resInfoList = requireContext().packageManager.queryIntentActivities(chooser, 0)
                    for (resolveInfo in resInfoList) {
                        val packageName = resolveInfo.activityInfo.packageName
                        requireContext().grantUriPermission(packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(chooser)

                    // Schedule deletion of temp file after a delay (ensure share dialog launched)
                    controlsHandler.postDelayed({
                        tempFile?.delete()
                        Log.d("MediaViewer", "Temporary share file deleted: ${tempFile?.name}")
                    }, 10000) // 10 seconds delay to be safe
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("MediaViewer", "Share error: ${e.message}", e)
            }
        }
    }

    /**
     * Retrieves the path to the folder where statuses are saved.
     * This function should be consistent across MediaViewerFragment and SavedFragment.
     */
    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val customPath = prefs.getString(Constants.KEY_SAVE_FOLDER_PATH, null)

        return if (!customPath.isNullOrEmpty()) {
            customPath
        } else {
            // Default to the app's primary external media directory first
            requireContext().externalMediaDirs.firstOrNull()?.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
                ?: // Fallback to primary external files directory if externalMediaDirs is null/empty
                (requireContext().getExternalFilesDir(null)?.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
                ?: // Last resort: app's internal files directory (less ideal for user-saved media)
                (requireContext().filesDir.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
        }
    }

    /**
     * Hides system UI (status bar and navigation bar) for immersive experience.
     */
    private fun hideSystemUI() {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Shows system UI (status bar and navigation bar).
     */
    private fun showSystemUI() {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        // Initialize player here if it's a video and not already initialized
        if (isVideo && player == null) {
            initializePlayer(mediaList[currentIndex])
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Ensure player resumes playback if it was paused in onPause
        player?.playWhenReady = playWhenReady // Use the saved playWhenReady state
    }

    override fun onPause() {
        super.onPause()
        // Pause playback when the fragment is no longer in the foreground
        player?.playWhenReady = false // Set playWhenReady to false to pause
        player?.pause() // Explicitly pause the player
    }

    override fun onStop() {
        super.onStop()
        // Release the player when the fragment is no longer visible
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showSystemUI() // Restore system UI when fragment is destroyed
        // Ensure player is released and bindings are nullified
        if (isVideo) {
            releasePlayer() // Ensure player is released one last time
            videoBinding?.videoPlayerView?.player = null
            videoBinding = null
        } else {
            imageBinding = null
        }
        // Ensure any pending temporary share files are deleted on destroy
        // This is a safety net for cases where the delayed deletion might not have run.
        requireContext().cacheDir.listFiles { file ->
            file.name.startsWith("share_temp_")
        }?.forEach { it.delete() }
    }
}