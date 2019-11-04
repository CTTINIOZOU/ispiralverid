## Adding Ver-ID to your own project

## iOS
1. Add the following entry in your app's **Info.plist**:

	~~~xml
	<key>com.ispiral.client_id</key>
	<string>[Client Id]</string>
	<key>com.ispiral.client_secret</key>
	<string>[Client Secret]</string>
	~~~
  
2. To include the SDK in your iOS project install CocoaPods and add the following pod spec in your project's Podfile:

	~~~groovy
    pod 'iSpiral-UI', '1.9.0'
	~~~

3. You can now open **MyProject.xcworkspace** in **Xcode** and Ver-ID will be available to use in your app **MyApp**.

## Running Ver-ID sessions
1. Before running a Ver-ID UI session you will need to import the `VerIDCore` framework and create an instance of `VerID`.
1. Have your class implement the `VerIDFactoryDelegate` protocol. You will receive a callback when the `VerID` instance is created or when the creation fails.
1. In the class that runs the Ver-ID session import `VerIDUI`.
1. Pass the `VerID` instance to the `VerIDSession` constructor along with the session settings.

### Example

~~~swift
import UIKit
import VerIDCore
import VerIDUI

class MyViewController: UIViewController, VerIDFactoryDelegate, VerIDSessionDelegate {
    
    func runLivenessDetection() {
        // You may want to display an activity indicator as the instance creation may take up to a few seconds
        let factory = VerIDFactory()
        // Set your class as the factory's delegate
        // The delegate methods will be called when the session is created or if the creation fails
        factory.delegate = self
        // Create an instance of Ver-ID
        factory.createVerID()
    }
    
    // MARK: - Ver-ID factory delegate
    
    func veridFactory(_ factory: VerIDFactory, didCreateVerID instance: VerID) {
        // Ver-ID instance was created
        // Create liveness detection settings
        let settings = LivenessDetectionSessionSettings()
        // Show the result of the session to the user
        settings.showResult = true
        // Create a Ver-ID UI session
        let session = VerIDSession(environment: instance, settings: settings)
        // Set your class as a delegate of the session to receive the session outcome
        session.delegate = self
        // Start the session
        session.start()
    }
    
    func veridFactory(_ factory: VerIDFactory, didFailWithError error: Error) {
        NSLog("Failed to create Ver-ID instance: %@", error.localizedDescription)
    }
    
    // MARK: - Session delegate
    
    func sessionWasCanceled(_ session: VerIDSession) {
        // Session was canceled
    }
    
    func session(_ session: VerIDSession, didFinishWithResult result: VerIDSessionResult) {
        // Session finished successfully
    }
    
    func session(_ session: VerIDSession, didFailWithError error: Error) {
        // Session failed
    }
}
~~~

## Android
1. Add the below line to the repositories in your app module's **gradle.build** file:
    
    ~~~groovy
    repositories {
        maven {
            url 'https://dev.ver-id.com/artifactory/gradle-release'
        }
    }
    ~~~
2. Add the following dependency to your **gradle.build** file:
	
	~~~groovy
    dependencies {
	    implementation 'com.ispiral.verid:ui:1.7.6'
    }
	~~~
2. Add RenderScript in your **gradle.build** file:

	~~~groovy
    android {
        defaultConfig {
            renderscriptTargetApi 14
            renderscriptSupportModeEnabled true
        }
    }
	~~~

3. Add the Client Id and Client Secret you received from iSpiral in your app's manifest XML:

	~~~xml
    <manifest>
        <application>
            <meta-data
                android:name="com.ispiral.client_id"
                android:value="yourClientId" />
            <meta-data
                android:name="com.ispiral.client_secret"
                android:value="yourClientSecret" />          
        </application>
    </manifest>
	~~~

## Usage

### Creating Ver-ID Environment
Prior to running Ver-ID sessions you will need to create an instance of Ver-ID.

~~~java
VerIDFactory verIDFactory = new VerIDFactory(getContext(), new VerIDFactoryDelegate() {
    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
        // You can now use the VerID instance
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
        // Failed to create an instance of Ver-ID
    }
});
verIDFactory.createVerID();
~~~

### Running Ver-ID Session Activities
~~~java
class MyActivity extends AppCompatActivity {

    static final int REQUEST_CODE_LIVENESS_DETECTION = 0;

    void startLivenessDetectionSession() {
        VerIDFactory veridFactory = new VerIDFactory(this, new VerIDFactoryDelegate() {
            @Override
            public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
                // You can now start a Ver-ID session
                LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
                settings.setNumberOfResultsToCollect(2);
                Intent intent = new VerIDSessionIntent(this, verID, settings);
                startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
            }

            @Override
            public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
                // Failed to create an instance of Ver-ID
            }
        });
        veridFactory.createVerID();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION && resultCode == RESULT_OK && data != null) {
            VerIDSessionResult sessionResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (sessionResult != null && sessionResult.getError() == null) {
                // Liveness detection session succeeded
            }
        }
    }
}
~~~

### Customizing Ver-ID Session Behaviour
The Ver-ID session activity finishes as the session concludes. If you want to change this or other behaviour of the session you can write your own activity class that extends `VerIDSessionActivity`.

### Controlling Liveness Detection
If you run a Ver-ID session with `LivenessDetectionSessionSettings` or its subclass `AuthenticationSessionSettings` you can control how Ver-ID detects liveness.

~~~java
LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
settings.setNumberOfResultsToCollect(3); // 1 straight plus 2 other poses
settings.setBearings(EnumSet.of(Bearing.STRAIGHT, Bearing.LEFT, Bearing.RIGHT)); // Limit the poses to left and right
~~~
The session result will contain 3 faces: 1 looking straight at the camera and 2 in random poses.

### Replacing Components in the Ver-ID Environment
You can have Ver-ID your own user management (face template storage) layer or even your own face detection and face recognition. To do that create an appropriate factory class and set it on the Ver-ID factory before calling the `createVerID()` method.

For example, to add your own storage layer:

~~~java
VerIDFactory verIDFactory = new VerIDFactory(this, this);
IUserManagementFactory userManagementFactory = new IUserManagementFactory() {
    @Override
    public IUserManagement createUserManagement() throws Exception {
        return new IUserManagement() {
            @Override
            public void assignFacesToUser(IRecognizable[] faces, String userId) throws Exception {
                
            }

            @Override
            public void deleteFaces(IRecognizable[] faces) throws Exception {

            }

            @Override
            public String[] getUsers() throws Exception {
                return new String[0];
            }

            @Override
            public IRecognizable[] getFacesOfUser(String userId) throws Exception {
                return new IRecognizable[0];
            }

            @Override
            public IRecognizable[] getFaces() throws Exception {
                return new IRecognizable[0];
            }

            @Override
            public void deleteUsers(String[] userIds) throws Exception {

            }

            @Override
            public void close() {

            }
        };
    }
};
verIDFactory.setUserManagementFactory(userManagementFactory);
verIDFactory.createVerID();
~~~
