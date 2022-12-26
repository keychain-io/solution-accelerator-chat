# Keychain Chat Sample Android

## Overview

This is a sample application that was created to demonstrate the practical use of Keychain for creating, managing, and using self sovereign identities for use in a chat application. While a chat application was used to demonstrate how Keychain can be used in a chat use case, the focus of this documentation will be on the use of Keychain and not the development of the chat app. The implementation details of the chat app will be glossed over, so that we can focus your attention on how to use Keychain in such a use case.

## Requirements
This project is written in Java for Android and requires Android Studio and Android SDK to build and deploy the chat app. All other requirements will be handled automatically by Gradle.

## Assumptions
We assume that you are already familiar with Java programming, Android App development, and Gradle and the MVVM design pattern. If you are not familiar with these tools and technologies, please take the time to find a tutorial on YouTube or Google. It is outside the scope of this document to teach these technologies.

## Building the Project

To build and run the sample application, please follow the steps below:

Open the project in Android Studio. If Android SDK is not found on your system, Android Studio will prompt you to choose or download it.

### Select the Build Variant

Select the build variant from the "Build" menu as follows:

`Build`->`Select Build Variant...`

In the Build Variants panel that is displayed, select `uatDebug`.

### Configuration

The files needed to run the chat app are located in the project's assets directory as shown in the following image:

![Architecture](./images/ConfigurationFiles.png)

#### keychain.cfg

The most important of these files is the keychain.cfg file, which is used to configure the Keychain SDK itself. It's contents look similar to the following:

```
[Gateway]
AutoRefreshCertificates=yes

[Blockchain]
PrimaryHost = 13.115.198.104
PrimaryQueryPort = 9091
PrimaryHeartbeatPort = 9092
PrimaryBlockPort = 9093
PrimaryTransactionPort = 9094
QueryRetries = 3
QueryTimeoutMsecs = 30000
SubscribeTimeoutMsecs = 3000

[Faucet]
PrimaryHost = 54.65.160.194
PrimaryPort = 3301

[TrustedDirectory]
PrimaryHost = 54.65.160.194
PrimaryPort = 3301

[License]
PrimaryHost = 54.65.160.194
PrimaryPort = 3301
ApiKey = YOURAPIKEYHERE

[Communication]
PrimaryHost = 54.65.160.194
PrimaryPort = 1883

[PairingLedger]
PairingChannel = UAT/chat/pairing/
DirectoryDomainPrefix = NAME-OF-YOUR-CHOOSING

```
In the section labeled `[License]` you will need to paste in a valid ApiKey where is says:

```
ApiKey = YOURAPIKEYHERE
```

Also, if you are operating from behind a firewall that blocks outgoing connections, you (or your network administrator) will need to create firewall rules to allow outgoing connections to the host ip addresses and ports contained in the keychain.cfg file. All ip addresses are for tcp/ip with the exception of the [TrustedDirectory], which uses http.

> It is possible that you will need to change the `ApiKey` under the `[License]` section. Please contact Keychain support at support@keychain.io to acquire an ApiKey if needed.

#### drop_keychain.sql and keychain.sql

The two files, `drop_keychain.sql` and `keychain.sql` are used by Keychain to drop and create the local Keychain database, which it uses internally. You do not need to care about these files, since they are used internally by Keychain.

#### application.properties

The chat application uses MQTT to send and receive both pairing and chat messages. The configuration for MQTT is contained in the `application.properties` file. It's contents is as follows:

```
mqtt.host = 54.65.160.194
mqtt.port = 1883

mqtt.channel.pairing = UAT/chat/pairing/
# also called the ledger in keychain.cfg
mqtt.channel.transfer = UAT/chat/messages/

trusted.directory.host = 54.65.160.194
trusted.directory.port = 3301
trusted.directory.domain.prefix = NAME-OF-YOUR-CHOOSING
```

> You may need to add a firewall rule for outgoing tcp/ip connections for the ip address and port.

You should also change the value of `trusted.directory.domain.prefix` to a unique string that represents a repository for chat app users to upload and share their URIs. This is used for pairing purposes.

#### Chats.sql

The file `Chats.sql` is a used to create the SQLite database that is used by the chat application to store chat information that is not stored or managed by Keychain itself, such as chat specific information. You can consider it the application level data needed for a basic chat application. In a real life chat application, similar or additional information may be stored on the device or off device. However, we wanted to keep everything self contained in this sample in order to eliminate the need to setup external dependencies, such as cloud data storage. And as you will see later in the documentation, we extend an interface in order to use the SQLite implementation. It would be fairly simple for you to extend the same interface and replace our SQLite implementation in order to interact with a different database as your needs may dictate.

#### Keychain SDK

The Keychain SDK is imported into the project using Gradle. The following are the Gradle dependencies:

```
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation files('libs/acssmc-1.1.5.jar')
    implementation 'com.google.android.material:material:1.4.0'
    implementation 'androidx.fragment:fragment:1.4.0-alpha01'
    implementation 'androidx.appcompat:appcompat:1.3.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.0'
    implementation 'androidx.lifecycle:lifecycle-extensions:2.2.0'
    implementation 'androidx.preference:preference:1.1.1'
    testImplementation 'junit:junit:4.13.1'
    androidTestImplementation 'androidx.test.ext:junit:1.1.3'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.4.0'
    api('org.eclipse.paho:org.eclipse.paho.android.service:1.1.1') {
        exclude module: 'support-v4'
    }
    api 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.4'
    api 'me.dm7.barcodescanner:zxing:1.9.8'
    api 'me.dm7.barcodescanner:zbar:1.9.8'

    // --- Keychain SDK HERE ---
    implementation 'io.keychain:libkeychain-android:2.4.9'

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation group: 'com.google.code.gson', name: 'gson', version: '2.9.0'

    implementation 'de.hdodenhof:circleimageview:3.1.0'

    implementation 'com.github.stfalcon:chatkit:0.3.3'
}
```

### Project Structure

The Keychain Chat sample application project is written using the MVVM (Model View ViewModel) design pattern. The project structure is as follows:

![Architecture](./images/MVVMProjectStructure.png)

* `models` contains the application level data classes. There are other data classes that are part of Keychain SDK
* `views` contains the user interface classes
* `viewmodel` contains the view model classes that implement the business logic and are used by the views to communicate with Keychain and the Chats SQLite database services.
* `interfaces` contains an interface called ChatRepository, with is implemented by the database service
  * You can also use it to implement a different database backend (such as cloud storage) for the chat sample without affecting the rest of the application
* `services` contains service classes that are used to talk to Keychain SDK and the SQLite database

## Running the Project

Build and run the project either on an emulator or through ADB onto an actual real phone.

## Chat High Level Overview

The following is a high level depiction of the architecture of the chat sample application.

![Architecture](./images/ChatHiLevelView.PNG)

The Keychain Chat sample application uses Blockchain technology for creating and managing sovereign identities. These identities are the digital identities of the device owner, which is known as a Persona, and the digital identities of owners of other devices, known as contacts. In order to send a message to another device running a Keychain chat application, you need to pair your device with that of a contact. Pairing can be performed by scanning a QR Code or by downloading the URIs of contacts from a trusted directory (an http server). Once paired, you can send messages to one contact at a time, or to all contacts at once using MQTT. Because the messages are signed and encrypted, only the contacts to whom you send messages will be able to decrypt and read those messages.

## Using the Application

When you run the chat sample application for the first time, you will need to create a persona. A persona is your sovereign digital identity. You will need it in order to login and use the chat application. You can create as many personas as you like.

![Architecture](./images/LoginScreenNoPersona.png)

Above is the login screen before any personas are created.

### Create Persona

To create a persona, tap the `CREATE NEW` button to bring up the Create Persona dialog as shown below. Then enter a first and last name for the persona, and tap `CREATE`.

![Architecture](./images/CreatePersonaScreen.png)

After creating the persona, it goes through the following status changes before it is fully matured and ready for use:

```
NOSTATUS
CREATED
FUNDING
BROADCASTED
CONFIRMING
CONFIRMED
EXPIRING
EXPIRED

```

Once the status reaches `CONFIRMED` it stays confirmed. EXPIRING and EXPIRED need not be dealt with in this sample application.

The status of the persona must be `CONFIRMED` before you can use it to login.

> Please note that it can take several minutes for the persona to become fully confirmed on the Blockchain.

### Login

The following screen shot shown several personas created, with one still not fully confirmed.

![Architecture](./images/LoginScreeManyPersonas.png)

To login to the chat application, touch the persona you want to login as. That will take you to the contacts screen. Additionally, the persona's URI will be uploaded to the trusted directory. And using MQTT, the application will subscribe to three topics:

* Messages sent to the logged in persona
* Messages sent to all personas
* Pairing requests/responses

### Contacts Screen

After logging in, you will be taken to the Contacts screen. Any contacts you are paired with will appear in the Contacts screen. Additionally, your persona's URI will also be automatically uploaded to the trusted directory. That will allow other devices that are running Keychain Chat to pair with your device by downloading your persona's URI.

![Architecture](./images/ContactScreenNoContacts.png)

To pair with another device, you can either scan the QR code of the other device running a copy of the Keychain Chat application, or you can have the other device scan your QR code, which is displayed above the list of contacts. Alternatively, you can download the URI's of all devices that have uploaded their persona URIs to the trusted directory.

To scan the QR code of another device, touch the QR code button. That will display the QR Code scanner, which you can then use to scan the other device's QR Code.

To pair with all devices running Keychain Chat that have uploaded their persona URI's to the trusted directory, touch the button to the left of the QR Code. Doing so will cause the application to:

* Download all the URIs from the trusted directory
* Send pair requests to each URI
* If the other device is running Keychain Chat
  * Received the pair request
  * Send pair response back to original sender
  * Call Keychain to create a contact from the pair request
  * The contact will then appear in the contact list
* Receive pair response
  * call Keychain to create a contact from the pair response
  * The contact will then appear in the contact list

The following shows the contacts screen after having paired with 3 other devices.

![Architecture](./images/ContactsScreenWithContacts.png)

### Sending Messages

One of the key features of Keychain is that only contacts that a message was intended for will be able to decrypt and read a message. In this chat sample application, you can either send messages one selected contact, or you can send messages to all contacts that you have paired with.

> Please remember that this is a sample application meant to focus on the use of Keychain. In so noting, the recipient of the message must be running and logged in. We may or may not enhance the sample in the future. However, this is a known limitation at the moment.

#### Chatting with One Contact

To chat with one contact, select the contact you want to chat with. Please make sure that the contact you want to chat with is also running Keychain Chat and is logged in. Tap the name of the contact you wish to chat with. This will bring up the conversation screen where you can then chat securely with one contact. As shown below:

![Architecture](./images/ConversationScreen.png)

The conversation screen is the tab to the far left at the bottom of the screen. Tapping it will display the chats of the last chat session.

#### Chatting with All Contacts

To chat with all contacts at once, who were downloaded from the trusted directory, tap the tab to the right of the conversation tab. Doing so will display the chats screen, which displays a list of contacts you have already been chatting with, one-to-one. Additionally, it contains the `ALL` chat.

![Architecture](./images/ChatsScreen.png)

Tapping the `ALL` row will allow you to chat with all contacts simultaneously, as shown below:

![Architecture](./images/ConversationScreenAllChat.png)

Whether or not your are chatting with one contact or all, only the contacts that you have paired with and intend to chat with, will be able to decrypt and display the messages.

## Technical Details

The Keychain Chat sample application for Android is written in Java using the Model View ViewModel design pattern.

### Initializing Keychain

Before using Keychain to encrypt/decrypt messages, Keychain's `Gateway` class must first be instantiated and initialized. This should only be performed once after starting the application and before using any Keychain functions. In this sample application we wrap the `Gateway` class in a service class called GatewayService. In turn GatewayService is contained in KeychainViewModel, which is used by any views or higher level view models that need to communicate with Keychain.

When KeychainViewModel is instantiated, it gets an singleton instance of GatewayService:

![Architecture](./images/InstanciateGateway.png)

Because KeychainApplication is a singleton, there is only one instance of GatewayService as well. Please keep that in mind, there should only be one instance of Keychain instantiated in your application. GatewayService is created when the application's onCreate method is called, as shown below:

![Architecture](./images/GatewayServiceSingleton.png)

And ultimately, instantiating Keychain is accomplished in the GatewayService by creating an instance of Keychain's Gateway class. After this is completed, it is okay to call methods of the GatewayService that will in turn call Keychain's Gateway class's methods.

![Architecture](./images/InstantiateKeychainGateway.png)

Please explore the other functions of the GatewayService class and how they relate to the function calls to Keychain's Gateway class.

## LEGAL DISCLAIMER

THIS SOFTWARE IS PROVIDED "AS-IS". KEYCHAIN EXPRESSLY DISCLAIMS ALL WARRANTIES, EXPRESS AND IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. KEYCHAIN DOES NOT WARRANT THAT THE SOFTWARE WILL MEET CLIENT'S REQUIREMENTS, THAT THE SOFTWARE IS COMPATIBLE WITH ANY PARTICULAR HARDWARE OR SOFTWARE PLATFORM, OR THAT THE OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR-FREE, OR THAT DEFECTS IN THE SOFTWARE WILL BE CORRECTED. THE ENTIRE RISK AS TO THE RESULTS AND PERFORMANCE OF THE SOFTWARE IS ASSUMED BY CLIENT. FURTHERMORE, KEYCHAIN DOES NOT WARRANT OR MAKE ANY REPRESENTATION REGARDING THE USE OR THE RESULTS OF THE USE OF THE SOFTWARE OR RELATED DOCUMENTATION IN TERMS OF THEIR CORRECTNESS, ACCURACY, QUALITY, RELIABILITY, APPROPRIATENESS FOR A PARTICULAR TASK OR APPLICATION, CURRENTNESS, OR OTHERWISE. NO ORAL OR WRITTEN INFORMATION OR ADVICE GIVEN BY KEYCHAIN OR KEYCHAIN'S AUTHORIZED REPRESENTATIVES SHALL CREATE A WARRANTY OR IN ANY WAY INCREASE THE SCOPE OF WARRANTIES PROVIDED.
