package com.racas.black;
import java.io.File;

/** Runs the exact APK download/validation code on a host JVM; does not run Android or the game. */
public class PublicDownloadCheck {
    public static void main(String[] args) throws Exception {
        String sources = "https://github.com/impacktmkt/black-android-local/releases/download/game-data-v1/BLACK.iso.001|1073741824\n"
                + "https://github.com/impacktmkt/black-android-local/releases/download/game-data-v1/BLACK.iso.002|1148452864";
        final int[] shown = {-1}; final String[] stage = {""};
        File image = new GameDownload().prepare(sources,new File(args[0]),new GameDownload.Cancellation(),(s,done,total)->{
            int pct=(int)(done*100/total);
            if(!s.equals(stage[0]) || pct>=shown[0]+5){stage[0]=s;shown[0]=pct;System.out.println(s+": "+pct+"% ("+done+"/"+total+")");}
        });
        System.out.println("PASS: anonymous public HTTPS download + Xbox header + exact ISO size + SHA-256: "+image.length()+" bytes");
    }
}
