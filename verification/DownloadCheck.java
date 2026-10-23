package com.racas.black;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class DownloadCheck {
    static int checks;
    static byte[] image = new byte[65536 + 20];
    static String hash;
    interface Action { void run() throws Exception; }
    static void check(boolean b, String why) { if (!b) throw new AssertionError(why); checks++; }
    static void rejects(Action a, String why) throws Exception {
        try { a.run(); throw new AssertionError("Accepted " + why); } catch (IOException expected) { checks++; }
    }
    static class Response extends HttpURLConnection {
        byte[] body; int code; long length; String range, location;
        Response(int status, byte[] content) throws IOException { super(new URL("https://example.invalid/file.iso")); code=status;body=content;length=content.length; }
        public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
        public int getResponseCode() { return code; }
        public long getContentLengthLong() { return length; }
        public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        public String getHeaderField(String name) { return name.equals("Content-Range") ? range : name.equals("Location") ? location : null; }
    }
    static GameDownload engine(Response response) { return new GameDownload(image.length,hash,u->response); }
    static GameDownload.Progress silent = (s,d,t)->{};
    static File folder(Path root,String name) throws IOException { return Files.createDirectory(root.resolve(name)).toFile(); }
    public static void main(String[] args) throws Exception {
        System.arraycopy("MICROSOFT*XBOX*MEDIA".getBytes("US-ASCII"),0,image,65536,20);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(image);StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format("%02x",b&255));hash=h.toString();
        Path root=Files.createTempDirectory("black-download-check-");
        try {
            File dir=folder(root,"full");
            Response full=new Response(200,image);
            File result=engine(full).prepare("https://example.invalid/file.iso",dir,new GameDownload.Cancellation(),silent);
            check(Arrays.equals(Files.readAllBytes(result.toPath()),image),"full data differs");
            check(!new File(dir,"BLACK.download.part").exists(),"partial not renamed");
            check("identity".equals(full.getRequestProperty("Accept-Encoding")),"compressed transfer enabled");
            dir=folder(root,"resume");File part=new File(dir,"BLACK.download.part");Files.write(part.toPath(),Arrays.copyOf(image,10000));
            Response resume=new Response(206,Arrays.copyOfRange(image,10000,image.length));resume.range="bytes 10000-"+(image.length-1)+"/"+image.length;
            result=engine(resume).prepare("https://example.invalid/file.iso",dir,new GameDownload.Cancellation(),silent);
            check("bytes=10000-".equals(resume.getRequestProperty("Range")),"resume Range missing");
            check(Arrays.equals(Files.readAllBytes(result.toPath()),image),"resumed data differs");
            dir=folder(root,"ignored-range");Files.write(new File(dir,"BLACK.download.part").toPath(),Arrays.copyOf(image,10000));
            result=engine(new Response(200,image)).prepare("https://example.invalid/file.iso",dir,new GameDownload.Cancellation(),silent);
            check(result.length()==image.length,"ignored Range was appended");
            File immutable=folder(root,"existing");Path existing=new File(immutable,"BLACK.iso").toPath();Files.write(existing,image);
            new GameDownload(image.length,hash,u->{throw new AssertionError("network used for existing image");}).prepare("",immutable,new GameDownload.Cancellation(),silent);checks++;
            byte[] wrong=image.clone();wrong[0]=1;File bad=folder(root,"bad-hash");
            rejects(()->engine(new Response(200,wrong)).prepare("https://example.invalid/file.iso",bad,new GameDownload.Cancellation(),silent),"bad hash");
            check(!new File(bad,"BLACK.iso").exists()&&!new File(bad,"BLACK.download.part").exists(),"bad hash committed or kept");
            File wrongMagic=folder(root,"bad-magic");byte[] random=new byte[image.length];
            rejects(()->engine(new Response(200,random)).prepare("https://example.invalid/file.iso",wrongMagic,new GameDownload.Cancellation(),silent),"bad Xbox header");
            File html=folder(root,"html");rejects(()->engine(new Response(200,"<html>login</html>".getBytes())).prepare("https://example.invalid/file.iso",html,new GameDownload.Cancellation(),silent),"HTML download");
            File forbidden=folder(root,"forbidden");rejects(()->engine(new Response(403,new byte[0])).prepare("https://example.invalid/file.iso",forbidden,new GameDownload.Cancellation(),silent),"HTTP 403");
            File range=folder(root,"wrong-range");Files.write(new File(range,"BLACK.download.part").toPath(),Arrays.copyOf(image,10000));
            Response badRange=new Response(206,Arrays.copyOfRange(image,10000,image.length));badRange.range="bytes 0-"+(image.length-1)+"/"+image.length;
            rejects(()->engine(badRange).prepare("https://example.invalid/file.iso",range,new GameDownload.Cancellation(),silent),"incorrect resume offset");
            check(new File(range,"BLACK.download.part").length()==10000,"original partial changed on invalid range");
            File cut=folder(root,"cut");Response truncated=new Response(200,Arrays.copyOf(image,12000));truncated.length=image.length;
            rejects(()->engine(truncated).prepare("https://example.invalid/file.iso",cut,new GameDownload.Cancellation(),silent),"truncated transfer");
            check(new File(cut,"BLACK.download.part").length()==12000,"interrupted bytes not retained");
            GameDownload.Cancellation cancel=new GameDownload.Cancellation();cancel.cancel();File stopped=folder(root,"cancelled");
            rejects(()->engine(new Response(200,image)).prepare("https://example.invalid/file.iso",stopped,cancel,silent),"cancelled operation");
            check(!new File(stopped,"BLACK.iso").exists(),"cancelled final image exists");
            for(String url:new String[]{"","http://example.invalid/a","https://user:password@example.invalid/a","invalid"})rejects(()->GameDownload.source(url),"insecure/missing source");
            Response redirect=new Response(302,new byte[0]);redirect.location="http://example.invalid/file.iso";File downgraded=folder(root,"downgrade");
            rejects(()->engine(redirect).prepare("https://example.invalid/file.iso",downgraded,new GameDownload.Cancellation(),silent),"HTTPS downgrade");
            // A Release can split the image across assets; the app joins them without extra files or user actions.
            int cutAt = 30000;
            byte[] first = Arrays.copyOf(image, cutAt), second = Arrays.copyOfRange(image, cutAt, image.length);
            String multipart = "https://example.invalid/one|"+first.length+"\nhttps://example.invalid/two|"+second.length;
            File combined = folder(root,"multipart");
            Response one = new Response(200,first), two = new Response(200,second);
            GameDownload multi = new GameDownload(image.length,hash,u->u.getPath().equals("/one")?one:two);
            File combinedImage = multi.prepare(multipart,combined,new GameDownload.Cancellation(),silent);
            check(Arrays.equals(Files.readAllBytes(combinedImage.toPath()),image),"multipart data differs");
            File resumedSecond = folder(root,"multipart-resume-second");
            Files.write(new File(resumedSecond,"BLACK.download.part").toPath(),Arrays.copyOf(image,cutAt+123));
            Response secondRange = new Response(206,Arrays.copyOfRange(second,123,second.length));
            secondRange.range = "bytes 123-"+(second.length-1)+"/"+second.length;
            GameDownload skipFirst = new GameDownload(image.length,hash,u->{if(!u.getPath().equals("/two"))throw new AssertionError("completed first part downloaded again");return secondRange;});
            File resumedImage = skipFirst.prepare(multipart,resumedSecond,new GameDownload.Cancellation(),silent);
            check("bytes=123-".equals(secondRange.getRequestProperty("Range")),"second part used global offset");
            check(Arrays.equals(Files.readAllBytes(resumedImage.toPath()),image),"second part resume differs");
            File ignoredSecond = folder(root,"multipart-ignored-range");
            Files.write(new File(ignoredSecond,"BLACK.download.part").toPath(),Arrays.copyOf(image,cutAt+123));
            File restarted = new GameDownload(image.length,hash,u->new Response(200,second)).prepare(multipart,ignoredSecond,new GameDownload.Cancellation(),silent);
            check(Arrays.equals(Files.readAllBytes(restarted.toPath()),image),"second restart erased first part");
            File resumedFirst = folder(root,"multipart-resume-first");
            Files.write(new File(resumedFirst,"BLACK.download.part").toPath(),Arrays.copyOf(image,10000));
            Response firstRange=new Response(206,Arrays.copyOfRange(first,10000,first.length));firstRange.range="bytes 10000-"+(first.length-1)+"/"+first.length;
            File resumedFirstImage = new GameDownload(image.length,hash,u->u.getPath().equals("/one")?firstRange:new Response(200,second)).prepare(multipart,resumedFirst,new GameDownload.Cancellation(),silent);
            check(Arrays.equals(Files.readAllBytes(resumedFirstImage.toPath()),image),"first part resume differs");
            File badConfig=folder(root,"bad-multipart-config");
            rejects(()->multi.prepare("https://example.invalid/one|10\nhttps://example.invalid/two|20",badConfig,new GameDownload.Cancellation(),silent),"incorrect total part size");
            rejects(()->multi.prepare("https://example.invalid/one|-1\nhttps://example.invalid/two|20",badConfig,new GameDownload.Cancellation(),silent),"negative part size");
            rejects(()->multi.prepare("https://example.invalid/one\nhttps://example.invalid/two",badConfig,new GameDownload.Cancellation(),silent),"missing part sizes");
            Files.write(existing,wrong);
            rejects(()->engine(new Response(200,image)).prepare("https://example.invalid/file.iso",immutable,new GameDownload.Cancellation(),silent),"invalid existing image");
            check(Arrays.equals(Files.readAllBytes(existing),wrong),"existing image overwritten");
            System.out.println("PASS: "+checks+" downloader assertions (host JVM; simulated HTTP, no Android/device test)");
        } finally { try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);} }
    }
}
