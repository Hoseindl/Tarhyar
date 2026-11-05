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

public class MainActivity extends Activity {
  WebView web; static final int PICK=10, SAVE=11; String pending="";
  @Override public void onCreate(Bundle state){super.onCreate(state); PDFBoxResourceLoader.init(getApplicationContext());
    web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setAllowFileAccess(true);
    web.getSettings().setDomStorageEnabled(true); web.addJavascriptInterface(new Bridge(),"Native");
    web.setWebViewClient(new WebViewClient()); setContentView(web); web.loadUrl("file:///android_asset/index.html"); }
  class Bridge {
    @JavascriptInterface public void pick(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});}
    @JavascriptInterface public void save(String html){pending=html;runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/html");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"tarh-dars.html");startActivityForResult(i,SAVE);});}
    @JavascriptInterface public void print(){runOnUiThread(()->{PrintManager m=(PrintManager)getSystemService(PRINT_SERVICE);m.print("طرح درس",web.createPrintDocumentAdapter("طرح درس"),new PrintAttributes.Builder().build());});}
  }
  void callback(String fn,String value){web.evaluateJavascript("window."+fn+"("+org.json.JSONObject.quote(value)+")",null);}
  @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
    if(req==PICK){Uri uri=data.getData();new Thread(()->{try(InputStream input=getContentResolver().openInputStream(uri)){
       File f=new File(getCacheDir(),"selected.pdf");try(OutputStream out=new FileOutputStream(f)){byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1)out.write(b,0,n);}
       try(PDDocument pdf=PDDocument.load(f)){int count=pdf.getNumberOfPages();StringBuilder result=new StringBuilder();PDFTextStripper stripper=new PDFTextStripper();
         for(int page=1;page<=count;page++){stripper.setStartPage(page);stripper.setEndPage(page);result.append("\n[[PAGE ").append(page).append("]]\n").append(stripper.getText(pdf));if(result.length()>1800000)break;}
         String payload=new org.json.JSONObject().put("pages",count).put("text",result.toString()).toString();runOnUiThread(()->callback("onBookLoaded",payload));
       }
     }catch(Exception ex){runOnUiThread(()->callback("onBookError",ex.getMessage()==null?"خطا در خواندن PDF":ex.getMessage()));}}).start();}
    if(req==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pending.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"فایل ذخیره شد",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"خطا در ذخیره",Toast.LENGTH_LONG).show();}}
  }
}
