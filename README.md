# WhatsApp Status Manager

## 📝 Overview

This Android application provides a robust and user-centric solution for the comprehensive management of WhatsApp statuses. Engineered with a paramount focus on performance optimization and a refined user interface, this software empowers individuals to effortlessly access, store, disseminate, and systematically organize their preferred status images and videos directly from their designated WhatsApp directories. This application effectively mitigates the challenge of ephemeral media, thereby ensuring that significant visual and auditory content is retained and readily available for user control.

## 📱 Screenshots

To facilitate a visual comprehension of the application's interface and functionality, a series of screenshots has been provided below. It is imperative that these placeholder image paths be replaced with authentic visual captures from the operational application. Users are advised to establish a directory named `screenshots` within the root of their GitHub repository and populate it with relevant `.png` or `.jpg` image files.

<p align="center">
<img src="screenshots/screenshot_status_list.png" alt="Status List Screen" width="250"/>
<img src="screenshots/screenshot_media_viewer.png" alt="Media Viewer Screen" width="250"/>
<img src="screenshots/screenshot_saved_list.png" alt="Saved List Screen" width="250"/>
<img src="screenshots/screenshot_settings.png" alt="Settings Screen" width="250"/>
</p>

## ✨ Features

* **WhatsApp Status Retrieval:** Within the "Status" section of the application, users are afforded the capability to seamlessly browse and review all recently published WhatsApp statuses. This encompasses both high-fidelity image updates and engaging video content, which are directly acquired from their original, typically concealed, WhatsApp directories. The application thus serves as a convenient and centralized platform for accessing transient media shared by contacts.

* **Integrated Media Playback and Viewing:** The application incorporates an intuitive media viewer, facilitating seamless playback of video content and display of image files, complemented by user-friendly controls.

* **Single-Action Media Preservation:** Users can readily archive any viewed status media to a designated local storage repository through a singular, efficient interaction.

* **Direct Content Dissemination:** The application enables the direct sharing of status media from the viewer interface to external applications.

* **Archived Media Management:** A dedicated "Saved" section provides access to all previously preserved images and videos.

* **Batch Selection and Operations:** The system supports efficient multi-selection capabilities, allowing users to execute batch operations, such as deletion or sharing, on multiple archived files concurrently.

* **Configurable Storage Location:** Within the application's settings, users possess the option to specify a custom folder path for the storage of saved statuses, with an accompanying cautionary notice regarding potential implications for device storage capacity.

* **Thematic Customization:** The application's aesthetic presentation can be personalized through the selection of Light, Dark, or System Default theme options.

* **Automated Status Archiving (Optional):** A robust feature permits the automatic preservation of all newly acquired statuses to a pre-defined, secure, and application-specific directory within the external media storage. This functionality offers a convenient backup mechanism and facilitates in-app access for collective management.

* **Integrated Storage Optimization:** In scenarios where device storage capacity is constrained, a dedicated utility within the settings allows for the expeditious removal of all archived statuses from the application's designated storage location, thereby obviating the necessity for manual file identification and deletion.

* **Privacy-Centric Design:** The application's architecture is predicated upon user trust, necessitating only the essential media access permissions for its fundamental operations.

* **Optimized Performance and Resource Utilization:** With an approximate installation footprint of 21 MB, this application is engineered for efficient operation even on devices characterized by limited memory resources, thereby providing a highly intuitive and performance-optimized user experience.

## 🛠️ Technical Architecture

* **Programming Language:** Kotlin

* **User Interface Framework:** XML, integrated with AndroidX libraries.

* **Architectural Pattern:** Fragment-based navigation, leveraging ViewModel/LiveData for data management (inferred from AndroidX library utilization).

* **Image and Video Loading:** [Glide](https://github.com/bumptech/glide) is employed for the efficient loading of images and video thumbnails.

* **Video Playback Engine:** [ExoPlayer](https://github.com/google/ExoPlayer) provides robust capabilities for video content playback.

* **Navigation Management:** The Android Jetpack Navigation Component facilitates seamless transitions between application fragments.

* **Persistent Configuration:** The AndroidX Preference Library is utilized for the management of user-defined application settings.

* **File System Interactions:** Standard Java I/O operations are implemented in adherence to Android's Scoped Storage best practices for secure and efficient file system management.

## 🚀 Deployment Procedures

To establish a local operational instance of this project, the following sequential steps are to be observed:

1. **Repository Cloning:**

   ```
   git clone [https://github.com/NKG-S/Save-Status.git](https://github.com/NKG-S/Save-Status.git)
   ```

2. **Project Initialization in Android Studio:**
   Initiate Android Studio and proceed to open the cloned project directory.

3. **Gradle Synchronization:**
   Permit the Gradle build system to synchronize and acquire all requisite dependencies.

4. **Application Execution:**
   Connect an Android device or launch an emulator, subsequently executing the application.

## 📖 Operational Guidelines

1. **WhatsApp Status Viewing:** Access the official WhatsApp application and view any statuses intended for preservation. This application is designed to retrieve statuses from WhatsApp's native media directories.

2. **Application Launch:** Initiate the WhatsApp Status Manager application.

3. **Status Navigation:** Proceed to the "Status" tab to review the available image and video statuses.

4. **Media Preservation:** Select any image or video to activate the media viewer. Utilize the "Save" button to transfer the content to the designated storage location. It is pertinent to note that if media is accessed from the "Saved" section, the "Save" button will be automatically concealed to prevent redundant data duplication.

5. **Archived Media Administration:** Transition to the "Saved" tab to oversee and manage your previously downloaded statuses. Long-press functionality enables the selection of multiple items for batch deletion or sharing operations.

6. **Configuration Adjustment:** Access the "Settings" tab to modify the application's theme, reconfigure the default save directory, or activate the "Automated Status Archiving" feature.

7. **Storage Optimization:** In instances of diminished storage capacity, the "Clean All Saved Statuses" option within the settings provides a mechanism to efficiently reclaim disk space by removing archived statuses directly from the application's designated storage, thereby eliminating the need for manual file location and removal.

## 🔒 Data Privacy and Access Permissions

The safeguarding of user privacy constitutes a paramount objective. This application has been meticulously designed with principles of transparency and user trust as foundational elements:

* **Required Permissions:** The application strictly necessitates access solely to media storage locations for the purpose of reading WhatsApp statuses and writing files to the user-specified directory.

* **Absence of Covert Data Collection:** An explicit declaration is made regarding the complete absence of any concealed data collection, tracking mechanisms, or the sharing of personal information or media with external entities.

* **User Confidence:** Users can repose confidence in this application for the secure and localized management of their statuses on their respective devices.

## 🤝 Collaborative Development

Contributions are integral to the advancement of open-source initiatives, fostering environments conducive to learning, inspiration, and innovation. All contributions are **sincerely appreciated**.

1. Fork the Project Repository.

2. Establish a Dedicated Feature Branch (`git checkout -b feature/DescriptiveFeatureName`).

3. Commit all Modifications (`git commit -m 'Implement a concise feature description'`).

4. Push Changes to the Branch (`git push origin feature/DescriptiveFeatureName`).

5. Submit a Pull Request for Review.

## 📄 Licensing Information

Distributed under the terms of the MIT License. Comprehensive details are available in the `LICENSE` file.

## 📬 Contact Information

\[Your Name/GitHub Username] - \[your.email@example.com]
Project Repository Link: <https://github.com/NKG-S/Save-Status>
