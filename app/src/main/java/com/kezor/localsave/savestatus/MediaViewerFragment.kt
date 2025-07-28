@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus // Corrected package name

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout // Import LinearLayout
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

class MediaViewerFragment : Fragment() {

    private val args: MediaViewerFragmentArgs by navArgs()
    private var imageBinding: FragmentImageViewerBinding? = null
    private var videoBinding: FragmentVideoPlayerBinding? = null

    private var player: ExoPlayer? = null
    private var isVideo: Boolean = false
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition = 0L

    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        videoBinding?.videoPlayerView?.hideController()
        // When controls hide, show the save/share action bar
        videoBinding?.appBarLayout?.visibility = View.VISIBLE // Show top app bar
        videoBinding?.actionBarContainer?.visibility = View.VISIBLE // Show bottom action bar
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val mediaItem = args.mediaItem
        isVideo = mediaItem.type == Constants.MEDIA_TYPE_VIDEO

        return if (isVideo) {
            videoBinding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
            videoBinding?.root ?: throw IllegalStateException("Video binding is null")
        } else {
            imageBinding = FragmentImageViewerBinding.inflate(inflater, container, false)
            imageBinding?.root ?: throw IllegalStateException("Image binding is null")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideSystemUI()

        val mediaItem = args.mediaItem
        val isSavedMedia = args.isSavedMedia

        if (isVideo) {
            initializePlayer(mediaItem)
            setupVideoUI(isSavedMedia)
        } else {
            setupImageViewer(mediaItem)
            setupImageUI(isSavedMedia)
        }
    }

    private fun setupVideoUI(isSavedMedia: Boolean) {
        videoBinding?.apply {
            topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }
            btnSave.visibility = if (isSavedMedia) View.GONE else View.VISIBLE
            btnSave.setOnClickListener { saveMedia(args.mediaItem) }
            btnShare.setOnClickListener { shareMedia(args.mediaItem) }

            // Set controller visibility listener for video player
            videoPlayerView.setControllerVisibilityListener(object : StyledPlayerView.ControllerVisibilityListener {
                override fun onVisibilityChanged(visibility: Int) {
                    val controlsLayout = videoBinding?.videoPlayerView?.findViewById<LinearLayout>(R.id.controls_layout)

                    if (visibility == View.VISIBLE) {
                        // Controller is visible, hide save/share buttons and top app bar
                        appBarLayout.visibility = View.GONE
                        actionBarContainer.visibility = View.GONE
                        controlsLayout?.visibility = View.VISIBLE // Explicitly show controls_layout
                        controlsHandler.removeCallbacks(hideControlsRunnable)
                        controlsHandler.postDelayed(hideControlsRunnable, 1500) // Hide after 1.5 seconds
                    } else {
                        // Controller is hidden, show save/share buttons and top app bar
                        appBarLayout.visibility = View.VISIBLE
                        actionBarContainer.visibility = View.VISIBLE
                        controlsLayout?.visibility = View.GONE // Explicitly hide controls_layout
                    }
                }
            })
        }
    }

    private fun setupImageUI(isSavedMedia: Boolean) {
        imageBinding?.apply {
            topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }
            btnSave.visibility = if (isSavedMedia) View.GONE else View.VISIBLE
            btnSave.setOnClickListener { saveMedia(args.mediaItem) }
            btnShare.setOnClickListener { shareMedia(args.mediaItem) }
        }
    }

    private fun setupImageViewer(mediaItem: MediaItem) {
        imageBinding?.imageFullScreen?.let { imageView ->
            Glide.with(this)
                .load(mediaItem.file)
                .into(imageView)
        }
    }

    private fun initializePlayer(mediaItem: MediaItem) {
        // Release any existing player before creating a new one
        if (player != null) {
            releasePlayer()
        }

        try {
            player = ExoPlayer.Builder(requireContext())
                .setSeekBackIncrementMs(10000) // 10 seconds seek back
                .setSeekForwardIncrementMs(10000) // 10 seconds seek forward
                .build()
                .also { exoPlayer ->
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
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to play video: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            updatePlayPauseButtonVisibility(exoPlayer.playWhenReady, state)
                            when (state) {
                                Player.STATE_BUFFERING, Player.STATE_READY -> {
                                    showControlsTemporarily()
                                }
                                Player.STATE_ENDED -> {
                                    // Rewind to beginning when video ends
                                    exoPlayer.seekTo(0)
                                    exoPlayer.playWhenReady = false // Pause after seeking to start
                                    updatePlayPauseButtonVisibility(false, Player.STATE_ENDED)
                                    showControlsTemporarily() // Show controls to allow replay
                                }
                            }
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            updatePlayPauseButtonVisibility(playWhenReady, exoPlayer.playbackState)
                        }
                    })
                    // Initial update of play/pause button visibility
                    updatePlayPauseButtonVisibility(exoPlayer.playWhenReady, exoPlayer.playbackState)
                }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Player initialization failed", e)
            Toast.makeText(
                requireContext(),
                "Video player initialization failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
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
        controlsHandler.postDelayed(hideControlsRunnable, 1500) // Use 1.5 seconds timeout
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            // Save state before releasing
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.stop() // Explicitly stop playback
            exoPlayer.release()
        }
        player = null
        controlsHandler.removeCallbacks(hideControlsRunnable) // Remove any pending callbacks
    }

    private fun saveMedia(mediaItem: MediaItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get the app's external media directory (e.g., /Android/media/com.kezor.localsave.savestatus/)
                // getExternalMediaDirs()[0] returns the primary external media directory
                val appMediaDir = requireContext().getExternalMediaDirs()[0]

                // Create the "SaveStatus" subfolder within the app's media directory
                val saveDir = File(appMediaDir, "SaveStatus")

                if (!saveDir.exists()) {
                    saveDir.mkdirs()
                }

                val destFile = File(saveDir, mediaItem.file.name)
                val success = Utils.copyFile(mediaItem.file, destFile)

                withContext(Dispatchers.Main) {
                    if (success) {
                        Utils.showToast(
                            requireContext(),
                            getString(R.string.status_saved_successfully)
                        )
                        Utils.scanMediaFile(requireContext(), destFile.absolutePath)
                        if (!args.isSavedMedia) {
                            videoBinding?.btnSave?.visibility = View.GONE
                            imageBinding?.btnSave?.visibility = View.GONE // Ensure image save button is also hidden
                        }
                    } else {
                        Utils.showToast(
                            requireContext(),
                            getString(R.string.status_save_failed)
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Utils.showToast(
                        requireContext(),
                        "Save failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun shareMedia(mediaItem: MediaItem) {
        try {
            val fileUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider", // Authority must match AndroidManifest.xml
                mediaItem.file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = when (mediaItem.type) {
                    Constants.MEDIA_TYPE_IMAGE -> "image/*"
                    Constants.MEDIA_TYPE_VIDEO -> "video/*"
                    else -> "*/*" // Fallback for unknown types
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.saved_share_button)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Sharing failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("MediaViewer", "Share error: ${e.message}", e)
        }
    }

    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val customPath = prefs.getString(Constants.KEY_SAVE_FOLDER_PATH, null)

        return if (!customPath.isNullOrEmpty()) {
            customPath
        } else {
            // Default to the app's external media directory
            requireContext().getExternalMediaDirs()[0].absolutePath + File.separator + "SaveStatus"
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
        // Initialize player here if it's a video and not already initialized
        if (isVideo && player == null) {
            initializePlayer(args.mediaItem)
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
    }
}
