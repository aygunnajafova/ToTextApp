package com.example.totext.aygun.najafova

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.Menu
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    //UI Views
    private lateinit var inputImageBtn: MaterialButton
    private lateinit var recognizeTextBtn: MaterialButton
    private lateinit var imageIv: ImageView
    private lateinit var recognizeTextEt: EditText

    private companion object {
        //to handle the result of Camera/Gallery permissions in onRequestPermissionResults
        private const val CAMERA_REQUEST_CODE = 100
        private const val STORAGE_REQUEST_CODE = 101
    }

    //Uri of the image that we will take from Camera/Gallery
    private var imageUri: Uri? = null

    //arrays of permission required to pick image from Camera/Gallery
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>

    //progress dialog
    private lateinit var progressDialog: ProgressDialog

    //Text Recognizer
    private lateinit var textRecognizer: TextRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init UI Views
        inputImageBtn = findViewById(R.id.inputImageBtn)
        recognizeTextBtn = findViewById(R.id.recognizeTextBtn)
        imageIv = findViewById(R.id.imageIv)
        recognizeTextEt = findViewById(R.id.recognizeTextEt)

        //init arrays of permissions required for Camera, Gallery
        cameraPermissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        storagePermissions = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)


        //init setup the progress dialog, show while text from image is being recognized
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside(false)

        //init TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        //handle click, show input image dialog
        inputImageBtn.setOnClickListener {
            showInputImageDialog()
        }


        recognizeTextBtn.setOnClickListener {
            if(imageUri == null) {
                showToast("Pick Image First...")
            }
            else {
                recognizeTextFromImage()
            }
        }

    }

    private fun recognizeTextFromImage() {
        //set message and show progress dialog
        progressDialog.setMessage("Preparing Image")
        progressDialog.show()

        try {
            //Prepare InputImage from image uri
            val inputImage = InputImage.fromFilePath(this,imageUri!!)
            //image prepared, we are about to start text recognition process, change progress message
            progressDialog.setMessage("Recognizing text...")
            //start text recognition process from image
            val textTaskResult = textRecognizer.process(inputImage)
                .addOnSuccessListener {text->
                    //process completed, dismiss dialog
                    progressDialog.dismiss()
                    //get the recognized text
                    val recognizeText = text.text
                    //set the recognized text to edit text
                    recognizeTextEt.setText(recognizeText)
                }
                .addOnFailureListener {e->
                    //failed recognized text from image, dismiss dialog, show reason in Toast
                    progressDialog.dismiss()
                    showToast("Failed to recognize text due to ${e.message}")
                }
        } catch (e: Exception){
            //Exception occurred while preparing InputImage, dismiss dialog, show reason in Toast
            showToast("Failed to prepare image due to ${e.message}")
        }
    }

    private fun showInputImageDialog() {
        //init PopupMenu param 1 is context, param 2 is UI View where you want to show PopupMenu
        val popupMenu = PopupMenu(this, inputImageBtn)

        //Add items Camera, Gallery to PopupMenu, param 2 is menu id,
        // param 3 is position of this menu item in menu items list,
        // param 4 is title of the menu
        popupMenu.menu.add(Menu.NONE,1,1,"CAMERA")
        popupMenu.menu.add(Menu.NONE,2,2,"GALLERY")

        //Show PopupMenu
        popupMenu.show()

        //handle PopupMenu item clicks
        popupMenu.setOnMenuItemClickListener { menuItem ->
            //get item id that is clicked from PopupMenu
            val id = menuItem.itemId
            if(id == 1) {
                //Camera is clicked, check if camera permissions are granted or not
                if(checkCameraPermission()) {
                    pickImageCamera()
                }else {
                    //camera permissions not granted, request the camera permissions
                    requestCameraPermissions()
                }
            }
            else if(id == 2) {
                //Gallery is clicked, check if storage permissions are granted or not
                if(checkStoragePermission()) {
                    pickImageGallery()
                } else {
                    //storage permission not granted, request the storage permissions
                    requestStoragePermission()
                }
            }

            return@setOnMenuItemClickListener true
        }
    }

    private fun pickImageGallery() {
        //intent to pick image from gallery, will show all resources from where we can pick the image
        val intent = Intent(Intent.ACTION_PICK)

        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    private val galleryActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            if(result.resultCode == Activity.RESULT_OK) {
                //image picked
                val data = result.data
                imageUri = data!!.data
                //set to imageView i.e. imageIv
                imageIv.setImageURI(imageUri)
            }
            else {
                //cancelled
                showToast("Cancelled...!")
            }
        }

    private fun pickImageCamera() {
        //get ready the image data to store in MediaStore
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Sample Title")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Description")

        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)
    }

    private val cameraActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            //here we will receive the image, if token from camera
            if(result.resultCode == Activity.RESULT_OK) {
                //image is token from camera
                //we already have the image in imageUri using function pickImageCamera
                imageIv.setImageURI(imageUri)
            }
            else {
                //cancelled
                showToast("Cancelled...")
            }
        }

    private fun checkStoragePermission() : Boolean {
        /*check if storage permission is allowed or not
        return true if allowed, false if not allowed*/

        return ContextCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkCameraPermission() : Boolean {
        /*check if camera & storage permissions are allowed or not
        return true if allowed, false if not allowed*/
        val cameraResult = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageResult = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        return cameraResult && storageResult
    }

    private fun requestStoragePermission() {
        //request storage permission (for gallery image pick)
        ActivityCompat.requestPermissions(this,storagePermissions, STORAGE_REQUEST_CODE)
    }

    private fun requestCameraPermissions() {
        //request camera permissions (for camera intent)
        ActivityCompat.requestPermissions(this,cameraPermissions, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //handle permission(s) results
        when(requestCode) {
            CAMERA_REQUEST_CODE -> {
                //Check if some action from permission dialog performed or not Allow/Deny
                if(grantResults.isNotEmpty()) {
                    //Check if Camera, Storage permissions granted, contains boolean results either true or false
                    val cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    //Check if both permissions are granted or not
                    if(cameraAccepted && storageAccepted) {
                        //both permissions (Camera & Gallery) are granted, we can launch camera intent
                        pickImageCamera()
                    } else {
                        //one or both permissions are denied, can't launch camera intent
                        showToast("Camera & Storage permissions are required...")
                    }

                }
            }

            STORAGE_REQUEST_CODE -> {
                //Check if some action from permission dialog performed or not Allow/Deny
                if(grantResults.isNotEmpty()) {
                    //Check if Storage permission granted, contains boolean results either true or false
                    val storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    //Check if storage permission are granted or not
                    if(storageAccepted) {
                        //storage permission granted, we can launch gallery intent
                        pickImageCamera()
                    } else {
                        //storage permission are denied, can't launch camera intent
                        showToast("Storage permission is required...")
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    }
}