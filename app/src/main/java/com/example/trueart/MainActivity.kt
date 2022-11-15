package com.example.trueart

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_brushsize.*
import kotlinx.android.synthetic.main.dialog_colors.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private var drawingView:DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    var customProgressDialog: Dialog? = null
    var colorSelectDialog: Dialog? = null
  //  var customColorDialog: Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode == RESULT_OK && result.data != null){
                iv_bg.setImageURI(result.data?.data)
            }
        }

    val requestPermissions: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted){
                   // Toast.makeText(this,"Permissions granted!!!", Toast.LENGTH_SHORT).show()
                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)

                }else{
                    if (permissionName==Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this,"You denied the permission ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = drawView
        drawingView?.setSizeForBrush(10.toFloat())

      //  mImageButtonCurrentPaint = ibColor.llColor[1] as ImageButton
    //    mImageButtonCurrentPaint = ibColor as ImageButton
    //    mImageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

        ibClear.setOnClickListener {
            drawingView?.onClickClear()
        }
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }
        ibColor.setOnClickListener {
            showColorChooserDialog()
        }
        ibGallery.setOnClickListener {
            Toast.makeText(this, "Double Tap to select the image :)",
                Toast.LENGTH_SHORT).show()
            requestStoragePermissions()
        }
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        ibSave.setOnClickListener {
            if (isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    saveImage(getBitmapFromView(fl_drawingView)).also {
                        cancelProgressDialog()
                    }
                }
            }
        }

    }

    private fun showColorChooserDialog(){
        val colorDialog = Dialog(this)
        colorDialog.setContentView(R.layout.dialog_colors)

        mImageButtonCurrentPaint = colorDialog.llBlack[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        ).also {
            hideColorChooserDialog()
        }
        colorDialog.setTitle("Colors: ")
        colorDialog.show()
    }
    private fun showBrushSizeChooserDialog(){
        val brushDialog=  Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brushsize)
        brushDialog.setTitle("Brush Size: ")
        brushDialog.pencil_brush.setOnClickListener {
            drawingView?.setSizeForBrush(3.toFloat())
            brushDialog.dismiss()
        }
      //  val smallBtn : ImageButton = brushDialog.findViewById(R.id.small_brush)
        brushDialog.small_brush.setOnClickListener {
            drawingView?.setSizeForBrush(5.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.middle_brush.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.large_brush.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
     //   Toast.makeText(this,"..,,,.", Toast.LENGTH_SHORT).show()
        if (view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.selectColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
          //  hideColorChooserDialog()

        }
        else{
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.selectColor(colorTag)
           // hideColorChooserDialog()
        }
    }
    fun hideColorChooserDialog(){
        val colorDialog = Dialog(this)
        colorDialog.setContentView(R.layout.dialog_colors)
        colorDialog.dismiss()
    }
    private fun isReadStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermissions(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("True Art","True App "+
                    "needs to access your External Storage")
        }else{
            requestPermissions.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun showRationalDialog(
        title: String, message:String
    ){
        val builder :AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }
    private fun  getBitmapFromView(view:View):Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,
        Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/*"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

    private fun saveImage(image: Bitmap?) {

        val random1 = Random().nextInt(520985)
        val random2 = Random().nextInt(520985)

        val name = "Drawing-${random1 + random2}"

        val data: OutputStream
        try {
            val resolver = contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "Drawing"
            )
            val imageUri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            data = resolver.openOutputStream(Objects.requireNonNull(imageUri)!!)!!
            image?.compress(Bitmap.CompressFormat.JPEG, 100, data)
            Objects.requireNonNull<OutputStream?>(data)
            Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Image Not Saved", Toast.LENGTH_SHORT).show()
        }

    }

    //another method to save the file, but it didn't work here for some reason
    private suspend fun saveBitmapFile(mBitmap:Bitmap?):String{
        var result = ""
        // val root = Environment.getExternalStorageDirectory().toString()
        //  val myDir = File("$root/captured_photo")
        //  myDir.mkdir()
        withContext(Dispatchers.IO){
            if (mBitmap != null){

                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.JPEG,100,bytes)
                    //     val file = File(myDir , "TrueArt_" + System.currentTimeMillis() /1000 +".jpg")
                    //   val ff = File(UUID.randomUUID().toString())

                    val f = File(
                        // getExternalFilesDir(null),
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "TrueArt_"

                                + System.currentTimeMillis() /1000 +".jpg")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                "File saved successfully :$result ",
                                Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong!!! ",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch (e:Exception){
                    result =""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
}

