package com.example.kidsdrawingapp

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    private lateinit var ib_brush:ImageButton
    private lateinit var ib_gallery:ImageButton
    private lateinit var ib_undo:ImageButton
    private lateinit var ib_save:ImageButton
    private lateinit var drawing_view:DrawingView
    private lateinit var iv_background:ImageView
    private lateinit var fl_drawing_view_container:FrameLayout
    private var mImageButtonCurrentPaint:ImageButton?=null
    private lateinit var ll_paint_colors:LinearLayout



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawing_view=findViewById(R.id.drawing_view)
        drawing_view.setSizeForBrush(20.toFloat()) // Setting the default brush size to drawing view.

//        drawing_view.setSizeForBrush(20.toFloat())

        ll_paint_colors=findViewById(R.id.ll_paint_colors)
        mImageButtonCurrentPaint=ll_paint_colors[1] as ImageButton

        mImageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

        ib_brush=findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener{
            showBrushSizeChooserDialog()
        }


        ib_gallery=findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener {
            if(isReadStorageAllowed()){

                val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(pickPhotoIntent, GALLERY)

            }
            else{
             requestStoragePermission()
            }
        }

        iv_background=findViewById(R.id.iv_background)

        ib_undo=findViewById(R.id.ib_undo)
        ib_undo.setOnClickListener {
            drawing_view.onClickUndo()
        }

        ib_save=findViewById(R.id.ib_save)
        fl_drawing_view_container=findViewById(R.id.fl_drawing_view_container)
        ib_save.setOnClickListener {
            if(isReadStorageAllowed()){
                BitmapAsyncTask(getBitmapFromView(fl_drawing_view_container)).execute()
            }else{
                requestStoragePermission()
            }
        }

    }

    private fun showBrushSizeChooserDialog(){

        //val drawingView:DrawingView?=null
        val brushDialog= Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBtn=brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawing_view.setSizeForBrush(10.toFloat())
         brushDialog.dismiss()
        }

        val mediumBtn=brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawing_view.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn=brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawing_view.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }


    fun paintClicked(view: View){
        if(view !== mImageButtonCurrentPaint){

            val imageButton = view as ImageButton
            val colorTag=imageButton.tag.toString()

            drawing_view.setColor(colorTag)

            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

            mImageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))
            mImageButtonCurrentPaint=view
        }
    }


    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE).toString())){
            Toast.makeText(this,"Need permission to add a background", Toast.LENGTH_SHORT).show()
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode== STORAGE_PERMISSION_CODE){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
             Toast.makeText(this, "Permission granted. Now you can add images", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(
                    this,
                    "Please grant permission to add image to background",
                    Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun isReadStorageAllowed():Boolean{

        val result= ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        return result==PackageManager.PERMISSION_GRANTED

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode==Activity.RESULT_OK){
            if(requestCode==GALLERY){

                try{
                    if(data!!.data!=null){

                        iv_background.visibility=View.VISIBLE
                        iv_background.setImageURI(data.data)
                    }

                    else{
                        Toast.makeText(this, "Error parsing the image or it is corrupted", Toast.LENGTH_SHORT).show()
                    }
                }catch (e: Exception){ e.printStackTrace()}

            }
        }
    }


    private fun getBitmapFromView(view: View): Bitmap {

        val returnedBitmap=Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas= Canvas(returnedBitmap)
        val bgDrawable=view.background
        if(bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }



    private inner class BitmapAsyncTask(val mBitmap: Bitmap): ViewModel(){

        fun execute() = viewModelScope.launch {
            onPreExecute()
            val result = doInBackground()
            onPostExecute(result)
        }

        private lateinit var mProgressDialog: Dialog

        private fun onPreExecute() {
            showProgressDialog()
        }

        private suspend fun doInBackground(): String = withContext(Dispatchers.IO) {
            var result = ""

            if(mBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                    val f = File(externalCacheDir!!.absoluteFile.toString()
                            + File.separator
                            + "KidsDrawingApp_"
                            + System.currentTimeMillis() / 1000
                            + ".png")

                    val fos = FileOutputStream(f)
                    fos.write(bytes.toByteArray())
                    fos.close()

                    result = f.absolutePath

                } catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
            return@withContext result
        }

        private fun onPostExecute(result: String?) {

            cancelDialog()
            if(!result!!.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "File Saved Succesfully : $result",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this@MainActivity,
                    "Something went wrong while saving file",
                    Toast.LENGTH_SHORT).show()
            }

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null){
                    path, uri -> val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.type = "image/png"
                startActivity(
                    Intent.createChooser(
                        shareIntent, "Share"
                    )
                )
            }

        }

        private fun showProgressDialog(){
            mProgressDialog = Dialog(this@MainActivity)
            mProgressDialog.setContentView(R.layout.dialog_custom_progress)
            mProgressDialog.show()
        }

        private fun cancelDialog(){
            mProgressDialog.dismiss()
        }

    }

    companion object{
        private const val STORAGE_PERMISSION_CODE=1
        private const val GALLERY=2
    }

}