package ir.teachfuture.tarhyar;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.*;
import android.widget.Toast;
import android.database.Cursor;
import android.provider.OpenableColumns;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private WebView web;
  private static final int PICK=10, SAVE=11;
  private String pending="";
  private String pendingMime="text/html";
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    PDFBoxResourceLoader.init(getApplicationContext());
    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setAllowFileAccess(true);
    web.getSettings().setDomStorageEnabled(true);
    web.addJavascriptInterface(new Bridge(),"Native");
    web.setWebViewClient(new WebViewClient());
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }
  class Bridge {
    @JavascriptInterface public void pick(){runOnUiThread(()->{
      Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      i.setType("application/pdf");
      i.addCategory(Intent.CATEGORY_OPENABLE);
      startActivityForResult(i,PICK);
    });}
    @JavascriptInterface public void save(String html,String format){
      pending=html;
      boolean word="word".equals(format);
      pendingMime=word?"application/msword":"text/html";
      runOnUiThread(()->{
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType(pendingMime);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,word?"tarhyar-lesson-plan.doc":"tarhyar-lesson-plan.html");
        startActivityForResult(i,SAVE);
      });
    }
    @JavascriptInterface public void print(){runOnUiThread(()->{
      PrintManager m=(PrintManager)getSystemService(PRINT_SERVICE);
      m.print("طرح درس طرح‌یار",web.createPrintDocumentAdapter("طرح درس"),new PrintAttributes.Builder().build());
    });}
  }
  private void callback(String fn,String value){runOnUiThread(()->web.evaluateJavascript("window."+fn+"("+JSONObject.quote(value)+")",null));}
  private void progress(long done,long total){
    try {JSONObject j=new JSONObject();j.put("done",done);j.put("total",total);callback("onBookProgress",j.toString());}catch(Exception ignored){}
  }
  @Override protected void onActivityResult(int req,int res,Intent data){
    super.onActivityResult(req,res,data);
    if(res!=RESULT_OK||data==null)return;
    if(req==PICK){
      Uri uri=data.getData();
      new Thread(()->{
        File f=new File(getCacheDir(),"tarhyar-selected.pdf");
        try {
          long total=-1;
          String filename="کتاب انتخاب‌شده.pdf";
          try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
              int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE);
              if(ni>=0)filename=c.getString(ni);
              if(si>=0&&!c.isNull(si))total=c.getLong(si);
            }
          }
          long done=0,previous=0;
          progress(0,total);
          try(InputStream input=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(f)){
            if(input==null)throw new IOException("فایل انتخاب‌شده قابل خواندن نیست");
            byte[] buffer=new byte[32768];int n;
            while((n=input.read(buffer))!=-1){
              out.write(buffer,0,n);done+=n;
              if(done-previous>=262144){progress(done,total);previous=done;}
            }
          }
          progress(done,total);
          try(PDDocument pdf=PDDocument.load(f)){
            int count=pdf.getNumberOfPages();
            StringBuilder text=new StringBuilder();
            PDFTextStripper stripper=new PDFTextStripper();
            boolean truncated=false;
            for(int page=1;page<=count;page++){
              stripper.setStartPage(page);stripper.setEndPage(page);
              text.append("\n[[PAGE ").append(page).append("]]\n").append(stripper.getText(pdf));
              if(text.length()>1500000){truncated=true;break;}
            }
            JSONObject result=new JSONObject();
            result.put("pages",count);result.put("bytes",done);result.put("name",filename);
            result.put("truncated",truncated);result.put("text",text.toString());
            callback("onBookLoaded",result.toString());
          }
        }catch(Exception ex){callback("onBookError",ex.getMessage()==null?"خطا در خواندن PDF":ex.getMessage());}
        finally {if(f.exists())f.delete();}
      }).start();
    }
    if(req==SAVE){
      try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
        if(out==null)throw new IOException("مسیر ذخیره در دسترس نیست");
        out.write(pending.getBytes(StandardCharsets.UTF_8));
        Toast.makeText(this,"فایل با موفقیت ذخیره شد",Toast.LENGTH_LONG).show();
      }catch(Exception e){Toast.makeText(this,"خطا در ذخیره فایل",Toast.LENGTH_LONG).show();}
    }
  }
  @Override public void onBackPressed(){
    web.evaluateJavascript("window.handleBack && window.handleBack()",null);
  }
}
