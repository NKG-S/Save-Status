@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalCoroutinesApi::class)

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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.kezor.localsave.savestatus.databinding.FragmentImageViewerBinding
import com.kezor.localsave.savestatus.databinding.FragmentVideoPlayerBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

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
    private var currentTabIndex: Int = 0  // Move this line up to here
    private var isFromSavedSection: Boolean = false
    private lateinit var sharedPreferences: SharedPreferences

    // Optimized handlers and runnables
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        hideAllControlsCompletely()
    }

    private lateinit var gestureDetector: GestureDetector

    // Performance optimizations
    private val fileHashCache = ConcurrentHashMap<String, String>()
    private var saveOperationJob: Job? = null
    private val _saveStateFlow = MutableStateFlow(SaveState.Unknown)
    private val saveStateFlow: StateFlow<SaveState> = _saveStateFlow.asStateFlow()

    // Background dispatcher for heavy operations
    private val backgroundDispatcher = Dispatchers.IO.limitedParallelism(2)

    // Channel for debouncing save checks
    private val saveCheckChannel = Channel<MediaItem>(Channel.CONFLATED)
    private var saveCheckJob: Job? = null

    private enum class SaveState {
        Unknown, Saved, NotSaved, Checking
    }

    companion object {
        private const val SWIPE_THRESHOLD = 100f
        private const val SWIPE_VELOCITY_THRESHOLD = 100f
        private const val CONTROLS_HIDE_DELAY = 1750L
        private const val TEMP_FILE_CLEANUP_DELAY = 10000L
        private const val BUFFER_SIZE = 32768 // 32KB
        private const val IMAGE_SIZE_LIMIT = 2048
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            mediaList = args.mediaList
            currentIndex = args.currentIndex
            isFromSavedSection = args.isSavedMedia // Use the correct parameter name
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Get current tab index from SharedPreferences
            currentTabIndex = sharedPreferences.getInt("current_tab_index", 0)

            if (!isFromSavedSection) {
                val savedFolderPath = getSavedFolderPath()
                isFromSavedSection = mediaList.any { it.file?.absolutePath!!.startsWith(savedFolderPath) }
            }

            setupGestureDetector()
            startSaveCheckProcessor()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onCreate", e)
            findNavController().navigateUp()
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                return if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) showPreviousMedia() else showNextMedia()
                    true
                } else {
                    false
                }
            }
        })
    }

    private fun startSaveCheckProcessor() {
        saveCheckJob = lifecycleScope.launch(backgroundDispatcher) {
            try {
                for (mediaItem in saveCheckChannel) {
                    if (isActive) {
                        checkSaveStatus(mediaItem)
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error in save check processor", e)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            val mediaItem = mediaList[currentIndex]
            isVideo = mediaItem.type == Constants.MEDIA_TYPE_VIDEO

            if (isVideo) {
                videoBinding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
                videoBinding?.root
            } else {
                imageBinding = FragmentImageViewerBinding.inflate(inflater, container, false)
                imageBinding?.root
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error creating view", e)
            null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            hideSystemUI()
            updateMediaView(mediaList[currentIndex])
            view.setOnTouchListener { _, event ->
                try {
                    gestureDetector.onTouchEvent(event)
                } catch (e: Exception) {
                    Log.e("MediaViewer", "Error handling touch event", e)
                    false
                }
            }

            // Observe save state changes
            observeSaveState()

            // Trigger save check for current media
            triggerSaveCheck(mediaList[currentIndex])
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onViewCreated", e)
        }
    }

    private fun observeSaveState() {
        lifecycleScope.launch {
            try {
                saveStateFlow.collect { state ->
                    if (isAdded && view != null) {
                        when (state) {
                            SaveState.Saved -> hideSaveButton()
                            SaveState.NotSaved -> showSaveButton()
                            SaveState.Checking -> {
                                // Optionally show loading state
                            }
                            SaveState.Unknown -> {
                                // Default state
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error observing save state", e)
            }
        }
    }

    private fun triggerSaveCheck(mediaItem: MediaItem) {
        try {
            saveCheckChannel.trySend(mediaItem)
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error triggering save check", e)
        }
    }

    @SuppressLint("UseKtx")
    private suspend fun checkSaveStatus(mediaItem: MediaItem) {
        try {
            if (!coroutineContext.isActive) return

            _saveStateFlow.value = SaveState.Checking

            val isSaved = when {
                isFromSavedSection -> true
                else -> {
                    val customUri = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
                    if (!customUri.isNullOrEmpty()) {
                        val folderDoc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(customUri))
                        folderDoc?.let { isMediaAlreadySavedInSAF(it, mediaItem) } ?: false
                    } else {
                        isMediaAlreadySaved(mediaItem)
                    }
                }
            }

            if (coroutineContext.isActive) {
                _saveStateFlow.value = if (isSaved) SaveState.Saved else SaveState.NotSaved
            }

        } catch (e: Exception) {
            Log.e("MediaViewer", "Error checking save status", e)
            if (coroutineContext.isActive) {
                _saveStateFlow.value = SaveState.Unknown
            }
        }
    }

    private fun hideSaveButton() {
        try {
            videoBinding?.btnSave?.visibility = View.GONE
            imageBinding?.btnSave?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error hiding save button", e)
        }
    }

    private fun showSaveButton() {
        try {
            if (!isFromSavedSection) {
                videoBinding?.btnSave?.visibility = View.VISIBLE
                imageBinding?.btnSave?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing save button", e)
        }
    }

    private fun updateMediaView(mediaItem: MediaItem) {
        try {
            releasePlayer()

            if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) {
                switchToVideoView(mediaItem)
            } else {
                switchToImageView(mediaItem)
            }

            // Trigger save check for new media
            triggerSaveCheck(mediaItem)
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error updating media view", e)
        }
    }

    private fun switchToVideoView(mediaItem: MediaItem) {
        try {
            if (videoBinding == null) {
                val container = view?.parent as? ViewGroup
                videoBinding = FragmentVideoPlayerBinding.inflate(layoutInflater, container, false)
                container?.addView(videoBinding?.root)
            }
            videoBinding?.root?.visibility = View.VISIBLE
            imageBinding?.root?.visibility = View.GONE
            initializePlayer(mediaItem)
            setupVideoUI()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error switching to video view", e)
        }
    }

    private fun switchToImageView(mediaItem: MediaItem) {
        try {
            if (imageBinding == null) {
                val container = view?.parent as? ViewGroup
                imageBinding = FragmentImageViewerBinding.inflate(layoutInflater, container, false)
                container?.addView(imageBinding?.root)
            }
            imageBinding?.root?.visibility = View.VISIBLE
            videoBinding?.root?.visibility = View.GONE
            setupImageViewer(mediaItem)
            setupImageUI()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error switching to image view", e)
        }
    }

    private fun setupVideoUI() {
        try {
            videoBinding?.apply {
                topAppBar.setNavigationOnClickListener {
                    try {
                        navigateBackWithTabRestoration()
                    } catch (e: Exception) {
                        Log.e("MediaViewer", "Error navigating up", e)
                    }
                }
                btnShare.visibility = View.VISIBLE
                btnSave.setOnClickListener { saveMedia(mediaList[currentIndex]) }
                btnShare.setOnClickListener { shareMedia(mediaList[currentIndex]) }

                videoPlayerView.setControllerVisibilityListener(createControllerVisibilityListener())
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error setting up video UI", e)
        }
    }

    private fun setupImageUI() {
        try {
            imageBinding?.apply {
                topAppBar.setNavigationOnClickListener {
                    try {
                        navigateBackWithTabRestoration()
                    } catch (e: Exception) {
                        Log.e("MediaViewer", "Error navigating up", e)
                    }
                }
                btnShare.visibility = View.VISIBLE
                btnSave.setOnClickListener { saveMedia(mediaList[currentIndex]) }
                btnShare.setOnClickListener { shareMedia(mediaList[currentIndex]) }
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error setting up image UI", e)
        }
    }

    private fun navigateBackWithTabRestoration() {
        try {
            val navController = findNavController()
            val previousBackStackEntry = navController.previousBackStackEntry

            // Pass the current tab index back to the previous fragment
            previousBackStackEntry?.savedStateHandle?.set("restore_tab_index", currentTabIndex)

            navController.navigateUp()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in navigateBackWithTabRestoration", e)
            findNavController().navigateUp()
        }
    }

    private fun createControllerVisibilityListener() = object : StyledPlayerView.ControllerVisibilityListener {
        override fun onVisibilityChanged(visibility: Int) {
            try {
                if (visibility == View.VISIBLE) {
                    // Show all controls immediately
                    showAllControlsImmediately()
                    // Remove any pending hide operations
                    controlsHandler.removeCallbacks(hideControlsRunnable)
                    // Schedule hide after delay
                    controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
                } else {
                    // Hide all controls immediately when ExoPlayer controller is hidden
                    hideAllControlsCompletely()
                }
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error in controller visibility listener", e)
            }
        }
    }

    private fun showAllControlsImmediately() {
        try {
            videoBinding?.apply {
                appBarLayout.visibility = View.GONE
                actionBarContainer.visibility = View.GONE

                // Show the built-in ExoPlayer controls including progress bar
                videoPlayerView.findViewById<LinearLayout>(R.id.controls_layout)?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing all controls", e)
        }
    }

    private fun hideAllControlsCompletely() {
        try {
            videoBinding?.apply {
                // Hide ExoPlayer's built-in controller first
                videoPlayerView.hideController()

                // Hide our custom controls
                appBarLayout.visibility = View.VISIBLE
                actionBarContainer.visibility = View.VISIBLE

                // Ensure the progress bar and all controls are hidden
                videoPlayerView.findViewById<LinearLayout>(R.id.controls_layout)?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error hiding all controls", e)
        }
    }

    private suspend fun saveMediaUsingSAF(mediaItem: MediaItem, folderUri: Uri, newFileName: String) {
        withContext(backgroundDispatcher) {
            try {
                val resolver = requireContext().contentResolver
                val folderDoc = DocumentFile.fromTreeUri(requireContext(), folderUri)

                if (folderDoc == null || !folderDoc.exists()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Cannot access save location", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext
                }

                // Check if file already exists (optimized)
                if (isMediaAlreadySavedInSAF(folderDoc, mediaItem)) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "This media is already saved", Toast.LENGTH_SHORT).show()
                            _saveStateFlow.value = SaveState.Saved
                        }
                    }
                    return@withContext
                }

                val mimeType = getMimeType(mediaItem)
                val newFileDoc = folderDoc.createFile(mimeType, newFileName)
                    ?: throw Exception("Failed to create file in selected location")

                // Optimized file copying with buffer
                copyFileOptimized(Uri.fromFile(mediaItem.file), newFileDoc.uri, resolver)

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Media saved successfully", Toast.LENGTH_SHORT).show()
                        _saveStateFlow.value = SaveState.Saved
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                throw Exception("SAF save failed: ${e.message}")
            }
        }
    }

    private fun getMimeType(mediaItem: MediaItem): String {
        return when (mediaItem.type) {
            Constants.MEDIA_TYPE_IMAGE -> "image/${mediaItem.file?.extension?.lowercase()}"
            Constants.MEDIA_TYPE_VIDEO -> "video/${mediaItem.file?.extension?.lowercase()}"
            else -> "*/*"
        }
    }

    private suspend fun copyFileOptimized(sourceUri: Uri, destUri: Uri, resolver: android.content.ContentResolver) {
        try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                resolver.openOutputStream(destUri)?.use { outputStream ->
                    copyStreamOptimized(inputStream, outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error copying file", e)
            throw e
        }
    }

    private suspend fun copyStreamOptimized(input: InputStream, output: OutputStream) {
        withContext(backgroundDispatcher) {
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    yield() // Allow other coroutines to run
                }
                output.flush()
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error copying stream", e)
                throw e
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private suspend fun saveMediaUsingFile(mediaItem: MediaItem, newFileName: String) {
        withContext(backgroundDispatcher) {
            try {
                val saveDir = File(getSavedFolderPath())
                if (!saveDir.exists()) saveDir.mkdirs()

                if (isMediaAlreadySaved(mediaItem, saveDir)) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "This media is already saved", Toast.LENGTH_SHORT).show()
                            _saveStateFlow.value = SaveState.Saved
                        }
                    }
                    return@withContext
                }

                val destFile = File(saveDir, newFileName)
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveMediaUsingMediaStore(mediaItem, newFileName)
                } else {
                    mediaItem.file?.let { copyFileOptimized(it, destFile) }
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (success == true) {
                            Toast.makeText(requireContext(), "Media saved successfully", Toast.LENGTH_SHORT).show()
                            _saveStateFlow.value = SaveState.Saved
                        } else {
                            Toast.makeText(requireContext(), "Failed to save media", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                throw Exception("File save failed: ${e.message}")
            }
        }
    }

    private suspend fun copyFileOptimized(source: File, dest: File): Boolean {
        return withContext(backgroundDispatcher) {
            try {
                source.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        copyStreamOptimized(input, output)
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("MediaViewer", "File copy failed", e)
                false
            }
        }
    }

    private fun showNextMedia() {
        try {
            if (currentIndex < mediaList.size - 1) {
                currentIndex++
                updateMediaView(mediaList[currentIndex])
            } else {
                Toast.makeText(requireContext(), "No more media to the right", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing next media", e)
        }
    }

    private fun showPreviousMedia() {
        try {
            if (currentIndex > 0) {
                currentIndex--
                updateMediaView(mediaList[currentIndex])
            } else {
                Toast.makeText(requireContext(), "No more media to the left", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing previous media", e)
        }
    }

    private fun setupImageViewer(mediaItem: MediaItem) {
        try {
            imageBinding?.imageFullScreen?.let { imageView ->
                val requestOptions = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .skipMemoryCache(false)
                    .override(IMAGE_SIZE_LIMIT, IMAGE_SIZE_LIMIT)

                Glide.with(this)
                    .load(mediaItem.file)
                    .apply(requestOptions)
                    .into(imageView)
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error setting up image viewer", e)
        }
    }

    private fun initializePlayer(mediaItem: MediaItem) {
        try {
            releasePlayer()

            player = ExoPlayer.Builder(requireContext())
                .setSeekBackIncrementMs(5000L)
                .setSeekForwardIncrementMs(5000L)
                .build().also { exoPlayer ->
                    videoBinding?.videoPlayerView?.player = exoPlayer
                    exoPlayer.playWhenReady = playWhenReady
                    exoPlayer.seekTo(currentWindow, playbackPosition)

                    val exoMediaItem = ExoMediaItem.fromUri(Uri.fromFile(mediaItem.file))
                    exoPlayer.setMediaItem(exoMediaItem)
                    exoPlayer.prepare()

                    setupControlButtons(exoPlayer)
                    exoPlayer.addListener(createPlayerListener(exoPlayer))
                    updatePlayPauseButtonVisibility(exoPlayer.playWhenReady, exoPlayer.playbackState)
                }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Video player initialization failed", e)
            Toast.makeText(requireContext(), "Video player initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createPlayerListener(exoPlayer: ExoPlayer) = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            if (isAdded) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to play video: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            try {
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
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error in playback state changed", e)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            try {
                updatePlayPauseButtonVisibility(playWhenReady, exoPlayer.playbackState)
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error in play when ready changed", e)
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun saveMedia(mediaItem: MediaItem) {
        try {
            if (isFromSavedSection) {
                Toast.makeText(requireContext(), "Media is already saved", Toast.LENGTH_SHORT).show()
                return
            }

            // Cancel any existing save operation
            saveOperationJob?.cancel()

            saveOperationJob = lifecycleScope.launch(backgroundDispatcher) {
                try {
                    val customUri = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
                    val newFileName = generateFileName(mediaItem)

                    if (!customUri.isNullOrEmpty()) {
                        saveMediaUsingSAF(mediaItem, Uri.parse(customUri), newFileName)
                    } else {
                        saveMediaUsingFile(mediaItem, newFileName)
                    }
                } catch (e: Exception) {
                    Log.e("MediaViewFragment", "Save failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error initiating save", e)
        }
    }

    private fun generateFileName(mediaItem: MediaItem): String {
        val fileId = UUID.randomUUID().toString().substring(0, 8)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val extension = mediaItem.file?.extension
        return "${fileId}_${mediaItem.file?.nameWithoutExtension}_$timestamp.$extension"
    }

    private suspend fun saveMediaUsingMediaStore(mediaItem: MediaItem, fileName: String): Boolean {
        return try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(mediaItem))
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
            }

            val collection = when (mediaItem.type) {
                Constants.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                Constants.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues) ?: return false

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(mediaItem.file).use { inputStream ->
                    copyStreamOptimized(inputStream, outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("MediaViewer", "MediaStore save failed", e)
            false
        }
    }

    private suspend fun isMediaAlreadySavedInSAF(folderDoc: DocumentFile, mediaItem: MediaItem): Boolean {
        return withContext(backgroundDispatcher) {
            try {
                val sourceHash = mediaItem.file?.let { getFileHashOptimized(it) }

                folderDoc.listFiles().any { file ->
                    try {
                        val tempFile = File.createTempFile("temp", null, requireContext().cacheDir)
                        requireContext().contentResolver.openInputStream(file.uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                copyStreamOptimized(input, output)
                            }
                        }
                        val targetHash = getFileHashOptimized(tempFile)
                        tempFile.delete()
                        sourceHash == targetHash
                    } catch (e: Exception) {
                        Log.e("MediaViewer", "Error checking SAF file", e)
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error checking SAF media", e)
                false
            }
        }
    }

    private suspend fun isMediaAlreadySaved(mediaItem: MediaItem, dir: File? = null): Boolean {
        return withContext(backgroundDispatcher) {
            try {
                val saveDir = dir ?: File(getSavedFolderPath())
                if (!saveDir.exists()) return@withContext false

                val sourceHash = mediaItem.file?.let { getFileHashOptimized(it) }

                saveDir.listFiles()?.any { file ->
                    try {
                        val targetHash = getFileHashOptimized(file)
                        sourceHash == targetHash
                    } catch (e: Exception) {
                        Log.e("MediaViewer", "Error checking file hash", e)
                        false
                    }
                } ?: false
            } catch (e: Exception) {
                Log.e("MediaViewer", "Error checking if media saved", e)
                false
            }
        }
    }

    private suspend fun getFileHashOptimized(file: File): String {
        val filePath = file.absolutePath

        // Check cache first
        fileHashCache[filePath]?.let { return it }

        return withContext(backgroundDispatcher) {
            try {
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(BUFFER_SIZE)

                file.inputStream().use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                        yield() // Allow other coroutines to run
                    }
                }

                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                fileHashCache[filePath] = hash // Cache the result
                hash
            } catch (e: Exception) {
                Log.e("MediaViewer", "Hash calculation failed for ${file.name}", e)
                ""
            }
        }
    }

    private fun setupControlButtons(exoPlayer: ExoPlayer) {
        try {
            videoBinding?.videoPlayerView?.apply {
                findViewById<ImageButton>(R.id.exo_play)?.setOnClickListener {
                    exoPlayer.play()
                    showControlsTemporarily()
                }
                findViewById<ImageButton>(R.id.exo_pause)?.setOnClickListener {
                    exoPlayer.pause()
                    showControlsTemporarily()
                }
                findViewById<ImageButton>(R.id.exo_rew)?.setOnClickListener {
                    exoPlayer.seekBack()
                    showControlsTemporarily()
                }
                findViewById<ImageButton>(R.id.exo_ffwd)?.setOnClickListener {
                    exoPlayer.seekForward()
                    showControlsTemporarily()
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error setting up control buttons", e)
        }
    }

    private fun updatePlayPauseButtonVisibility(playWhenReady: Boolean, playbackState: Int) {
        try {
            val playButton = videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_play)
            val pauseButton = videoBinding?.videoPlayerView?.findViewById<ImageButton>(R.id.exo_pause)

            if (playButton != null && pauseButton != null) {
                val shouldShowPause = playWhenReady &&
                        playbackState != Player.STATE_ENDED &&
                        playbackState != Player.STATE_IDLE

                playButton.visibility = if (shouldShowPause) View.GONE else View.VISIBLE
                pauseButton.visibility = if (shouldShowPause) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error updating play/pause button visibility", e)
        }
    }

    private fun showControlsTemporarily() {
        try {
            videoBinding?.videoPlayerView?.showController()
            controlsHandler.removeCallbacks(hideControlsRunnable)
            controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing controls temporarily", e)
        }
    }

    private fun releasePlayer() {
        try {
            player?.let { exoPlayer ->
                playbackPosition = exoPlayer.currentPosition
                currentWindow = exoPlayer.currentMediaItemIndex
                playWhenReady = exoPlayer.playWhenReady
                exoPlayer.stop()
                exoPlayer.release()
            }
            player = null
            controlsHandler.removeCallbacks(hideControlsRunnable)
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error releasing player", e)
        }
    }

    private fun shareMedia(mediaItem: MediaItem) {
        lifecycleScope.launch(backgroundDispatcher) {
            var tempFile: File? = null
            try {
                val cacheDir = requireContext().cacheDir
                tempFile = File(cacheDir, "share_temp_${mediaItem.file?.name}")

                val success = mediaItem.file?.let { copyFileOptimized(it, tempFile) }

                if (!success!!) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Failed to prepare file for sharing.", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }

                val fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    tempFile
                )

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                type = getMimeType(mediaItem)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            val chooser = Intent.createChooser(shareIntent, "Share Media")
                            startActivity(chooser)

                            // Clean up temp file after delay
                            controlsHandler.postDelayed({
                                tempFile.delete()
                            }, TEMP_FILE_CLEANUP_DELAY)
                        } catch (e: Exception) {
                            Log.e("MediaViewer", "Error starting share intent", e)
                            Toast.makeText(requireContext(), "Sharing failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                tempFile?.delete()
                Log.e("MediaViewer", "Error sharing media", e)
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun getSavedFolderPath(): String {
        return try {
            val customUri = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)

            if (!customUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(customUri)
                    val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                    docFile?.uri?.path ?: getDefaultSavePath()
                } catch (e: Exception) {
                    Log.e("MediaViewer", "Error parsing custom URI", e)
                    getDefaultSavePath()
                }
            } else {
                getDefaultSavePath()
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error getting saved folder path", e)
            getDefaultSavePath()
        }
    }

    private fun getDefaultSavePath(): String {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error getting default save path", e)
            File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                Constants.APP_SAVE_SUBDIRECTORY_NAME).absolutePath
        }
    }

    private fun hideSystemUI() {
        try {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error hiding system UI", e)
        }
    }

    private fun showSystemUI() {
        try {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error showing system UI", e)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            if (isVideo && player == null) {
                initializePlayer(mediaList[currentIndex])
            }
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onStart", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            hideSystemUI()
            player?.playWhenReady = playWhenReady
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            player?.playWhenReady = false
            player?.pause()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onPause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            releasePlayer()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onStop", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            showSystemUI()

            // Cancel all ongoing operations
            saveOperationJob?.cancel()
            saveCheckJob?.cancel()
            saveCheckChannel.close()

            if (isVideo) {
                releasePlayer()
                videoBinding?.videoPlayerView?.player = null
                videoBinding = null
            } else {
                imageBinding = null
            }

            cleanupTempFiles()

            fileHashCache.clear()
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onDestroyView", e)
        }
    }

    private fun cleanupTempFiles() {
        try {
            requireContext().cacheDir.listFiles { file ->
                file.name.startsWith("share_temp_") || file.name.startsWith("temp")
            }?.forEach { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.w("MediaViewer", "Failed to delete temp file: ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.w("MediaViewer", "Failed to cleanup temp files", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            controlsHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e("MediaViewer", "Error in onDestroy", e)
        }
    }
}