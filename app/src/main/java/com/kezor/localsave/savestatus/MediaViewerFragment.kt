@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val mediaItem = args.mediaItem
        isVideo = mediaItem.type == Constants.MEDIA_TYPE_VIDEO

        return if (isVideo) {
            videoBinding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
            videoBinding?.root
        } else {
            imageBinding = FragmentImageViewerBinding.inflate(inflater, container, false)
            imageBinding?.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ThemeManager.applyTheme(requireContext())

        hideSystemUI()

        val mediaItem = args.mediaItem
        val isSavedMedia = args.isSavedMedia

        if (isVideo) {
            setupVideoPlayer(mediaItem)
            videoBinding?.topAppBar?.setNavigationOnClickListener { findNavController().navigateUp() }
            videoBinding?.btnSave?.visibility = if (isSavedMedia) View.GONE else View.VISIBLE
            videoBinding?.btnSave?.setOnClickListener { saveMedia(mediaItem) }
            videoBinding?.btnShare?.setOnClickListener { shareMedia(mediaItem) }
        } else {
            setupImageViewer(mediaItem)
            imageBinding?.topAppBar?.setNavigationOnClickListener { findNavController().navigateUp() }
            imageBinding?.btnSave?.visibility = if (isSavedMedia) View.GONE else View.VISIBLE
            imageBinding?.btnSave?.setOnClickListener { saveMedia(mediaItem) }
            imageBinding?.btnShare?.setOnClickListener { shareMedia(mediaItem) }
        }
    }

    private fun setupImageViewer(mediaItem: MediaItem) {
        imageBinding?.apply {
            Glide.with(this@MediaViewerFragment)
                .load(mediaItem.file)
                .into(imageFullScreen)
        }
    }

    private fun setupVideoPlayer(mediaItem: MediaItem) {
        player = ExoPlayer.Builder(requireContext()).build().also { exoPlayer ->
            videoBinding?.videoPlayerView?.player = exoPlayer
            val mediaSource = ExoMediaItem.fromUri(Uri.fromFile(mediaItem.file))
            exoPlayer.setMediaItem(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun saveMedia(mediaItem: MediaItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            val saveFolderPath = getSavedFolderPath()
            val destFile = File(saveFolderPath, mediaItem.file.name)

            val success = Utils.copyFile(mediaItem.file, destFile)

            withContext(Dispatchers.Main) {
                if (success) {
                    Utils.showToast(requireContext(), getString(R.string.status_saved_successfully))
                    Utils.scanMediaFile(requireContext(), destFile.absolutePath)
                    if (!args.isSavedMedia) {
                        if (isVideo) videoBinding?.btnSave?.visibility = View.GONE
                        else imageBinding?.btnSave?.visibility = View.GONE
                    }
                } else {
                    Utils.showToast(requireContext(), getString(R.string.status_save_failed))
                }
            }
        }
    }

    private fun shareMedia(mediaItem: MediaItem) {
        val fileUri = Uri.fromFile(mediaItem.file)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, fileUri)
            type = mediaItem.type + "/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.saved_share_button)))
    }

    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getString(Constants.KEY_SAVE_FOLDER_PATH, null)
            ?: (requireContext().getExternalFilesDir(null)?.absolutePath + File.separator + Constants.DEFAULT_SAVE_FOLDER_NAME)
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
        if (isVideo) {
            player?.playWhenReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (isVideo && player == null) {
            setupVideoPlayer(args.mediaItem)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isVideo) {
            player?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isVideo) {
            player?.release()
            player = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showSystemUI()
        imageBinding = null
        videoBinding = null
    }
}
