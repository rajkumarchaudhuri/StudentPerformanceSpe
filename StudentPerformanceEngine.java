import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;


public class StudentPerformanceEngine {

    static int PORT = 8080;
    static String DIR = "spe_data";
    static String UF  = DIR + "/users.json";
    static String GF  = DIR + "/grades.json";
    static String RF  = DIR + "/remarks.json";

    // in-memory storage maps
    static Map<String,Map<String,String>> udb = new LinkedHashMap<>(); // users
    static Map<String,Map<String,String>> gdb = new LinkedHashMap<>(); // grades
    static List<Map<String,String>>       rdb = new ArrayList<>();     // remarks

    public static void main(String[] a) throws Exception {
        new File(DIR).mkdirs();
        load();
        HttpServer s = HttpServer.create(new InetSocketAddress(PORT), 0);
        s.createContext("/",             e -> page(e));
        s.createContext("/api/register", e -> reg(e));
        s.createContext("/api/login",    e -> login(e));
        s.createContext("/api/grades",   e -> grades(e));
        s.createContext("/api/remarks",  e -> remarks(e));
        s.createContext("/api/students", e -> students(e));
        s.setExecutor(Executors.newFixedThreadPool(4));
        s.start();
        System.out.println("Running at http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop");
    }

    // load / save

    static void load() {
        try { udb = readMM(UF); } catch(Exception e) { udb = new LinkedHashMap<>(); }
        try { gdb = readMM(GF); } catch(Exception e) { gdb = new LinkedHashMap<>(); }
        try { rdb = readLM(RF); } catch(Exception e) { rdb = new ArrayList<>(); }
        System.out.println("Loaded " + udb.size() + " users, " + gdb.size() + " grades, " + rdb.size() + " remarks");
    }

    static synchronized void save() {
        try {
            wf(UF, mm2j(udb));
            wf(GF, mm2j(gdb));
            wf(RF, lm2j(rdb));
        } catch(Exception e) { System.out.println("Save error: " + e.getMessage()); }
    }

    // json helpers 

    static String mm2j(Map<String,Map<String,String>> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean f = true;
        for (String k : m.keySet()) { if(!f) sb.append(","); f=false; sb.append("\"").append(esc(k)).append("\":").append(m2j(m.get(k))); }
        return sb.append("}").toString();
    }

    static String lm2j(List<Map<String,String>> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<l.size();i++) { if(i>0) sb.append(","); sb.append(m2j(l.get(i))); }
        return sb.append("]").toString();
    }

    static String m2j(Map<String,String> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean f = true;
        for (String k : m.keySet()) { if(!f) sb.append(","); f=false; sb.append("\"").append(esc(k)).append("\":\"").append(esc(m.get(k))).append("\""); }
        return sb.append("}").toString();
    }

    static Map<String,Map<String,String>> readMM(String path) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8).trim();
        Map<String,Map<String,String>> res = new LinkedHashMap<>();
        raw = raw.substring(1, raw.length()-1).trim();
        if (raw.isEmpty()) return res;
        int i=0;
        while (i<raw.length()) {
            while (i<raw.length() && " ,\n\r\t".indexOf(raw.charAt(i))>=0) i++;
            if (i>=raw.length()||raw.charAt(i)!='"') { i++; continue; }
            i++; StringBuilder key=new StringBuilder();
            while (i<raw.length()&&raw.charAt(i)!='"') { if(raw.charAt(i)=='\\') i++; if(i<raw.length()) key.append(raw.charAt(i++)); }
            i++;
            while (i<raw.length()&&" :".indexOf(raw.charAt(i))>=0) i++;
            if (i>=raw.length()||raw.charAt(i)!='{') { i++; continue; }
            int st=i, d=0;
            while (i<raw.length()) { if(raw.charAt(i)=='{') d++; else if(raw.charAt(i)=='}'){d--;if(d==0){i++;break;}} i++; }
            res.put(key.toString(), parseObj(raw.substring(st,i)));
        }
        return res;
    }

    static List<Map<String,String>> readLM(String path) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8).trim();
        List<Map<String,String>> res = new ArrayList<>();
        raw = raw.substring(1, raw.length()-1).trim();
        if (raw.isEmpty()) return res;
        int i=0;
        while (i<raw.length()) {
            while (i<raw.length()&&" ,\n\r\t".indexOf(raw.charAt(i))>=0) i++;
            if (i>=raw.length()||raw.charAt(i)!='{') { i++; continue; }
            int st=i, d=0;
            while (i<raw.length()) { if(raw.charAt(i)=='{') d++; else if(raw.charAt(i)=='}'){d--;if(d==0){i++;break;}} i++; }
            res.add(parseObj(raw.substring(st,i)));
        }
        return res;
    }

    static Map<String,String> parseObj(String json) {
        Map<String,String> m = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json=json.substring(1);
        if (json.endsWith("}")) json=json.substring(0,json.length()-1);
        int i=0;
        while (i<json.length()) {
            while (i<json.length()&&" ,\n\r\t".indexOf(json.charAt(i))>=0) i++;
            if (i>=json.length()||json.charAt(i)!='"') { i++; continue; }
            i++; StringBuilder k=new StringBuilder();
            while (i<json.length()&&json.charAt(i)!='"') { if(json.charAt(i)=='\\'){i++;if(i<json.length())k.append(unc(json.charAt(i++)));continue;} k.append(json.charAt(i++)); }
            i++;
            while (i<json.length()&&" :".indexOf(json.charAt(i))>=0) i++;
            if (i>=json.length()) break;
            String v;
            if (json.charAt(i)=='"') {
                i++; StringBuilder vb=new StringBuilder();
                while (i<json.length()&&json.charAt(i)!='"') { if(json.charAt(i)=='\\'){i++;if(i<json.length())vb.append(unc(json.charAt(i++)));continue;} vb.append(json.charAt(i++)); }
                i++; v=vb.toString();
            } else { int st=i; while(i<json.length()&&json.charAt(i)!=','&&json.charAt(i)!='}')i++; v=json.substring(st,i).trim(); }
            m.put(k.toString(),v);
        }
        return m;
    }

    static String jv(String json, String key) {
        int idx=json.indexOf("\""+key+"\""); if(idx<0) return "";
        idx+=key.length()+2;
        while(idx<json.length()&&" :".indexOf(json.charAt(idx))>=0) idx++;
        if(idx>=json.length()) return "";
        if(json.charAt(idx)=='"') {
            idx++; StringBuilder sb=new StringBuilder();
            while(idx<json.length()&&json.charAt(idx)!='"'){if(json.charAt(idx)=='\\'){idx++;if(idx<json.length())sb.append(unc(json.charAt(idx++)));continue;}sb.append(json.charAt(idx++));}
            return sb.toString();
        }
        int st=idx; while(idx<json.length()&&json.charAt(idx)!=','&&json.charAt(idx)!='}')idx++;
        return json.substring(st,idx).trim();
    }

    static String esc(String s) { if(s==null)return""; return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r"); }
    static char unc(char c) { return c=='n'?'\n':c=='t'?'\t':c; }
    static void wf(String p, String c) throws Exception { PrintWriter pw=new PrintWriter(new FileWriter(p)); pw.print(c); pw.close(); }
    static String body(HttpExchange ex) throws IOException { return new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); }

    static void json(HttpExchange ex, int code, String data) throws IOException {
        byte[] b=data.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        ex.sendResponseHeaders(code,b.length);
        ex.getResponseBody().write(b); ex.getResponseBody().close();
    }

    static boolean cors(HttpExchange ex) throws IOException {
        if(!"OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) return false;
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
        ex.sendResponseHeaders(204,-1); return true;
    }

    // ---------- route handlers ----------

    static void page(HttpExchange ex) throws IOException {
        byte[] b=html().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
        ex.sendResponseHeaders(200,b.length); ex.getResponseBody().write(b); ex.getResponseBody().close();
    }

    static void reg(HttpExchange ex) throws IOException {
        if(cors(ex)) return;
        try {
            String bd=body(ex);
            String em=jv(bd,"email").toLowerCase().trim(), nm=jv(bd,"name").trim(), pw=jv(bd,"password"), rl=jv(bd,"role");
            if(em.isEmpty()||nm.isEmpty()||pw.isEmpty()){json(ex,400,"{\"error\":\"Fill all fields\"}");return;}
            if(udb.containsKey(em)){json(ex,409,"{\"error\":\"Email already registered\"}");return;}
            Map<String,String> u=new LinkedHashMap<>();
            u.put("name",nm); u.put("email",em); u.put("password",pw); u.put("role",rl);
            u.put("date",new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            udb.put(em,u); save();
            json(ex,200,"{\"ok\":true,\"role\":\""+esc(rl)+"\",\"name\":\""+esc(nm)+"\"}");
        } catch(Exception e){json(ex,500,"{\"error\":\"Server error\"}");}
    }

    static void login(HttpExchange ex) throws IOException {
        if(cors(ex)) return;
        try {
            String bd=body(ex);
            String em=jv(bd,"email").toLowerCase().trim(), pw=jv(bd,"password");
            Map<String,String> u=udb.get(em);
            if(u==null){json(ex,401,"{\"error\":\"No account with this email\"}");return;}
            if(!u.get("password").equals(pw)){json(ex,401,"{\"error\":\"Wrong password\"}");return;}
            json(ex,200,"{\"ok\":true,\"role\":\""+esc(u.get("role"))+"\",\"name\":\""+esc(u.get("name"))+"\",\"email\":\""+esc(em)+"\"}");
        } catch(Exception e){json(ex,500,"{\"error\":\"Server error\"}");}
    }

    static void grades(HttpExchange ex) throws IOException {
        if(cors(ex)) return;
        try {
            if("GET".equals(ex.getRequestMethod())) {
                String q=ex.getRequestURI().getQuery();
                String em=(q!=null&&q.startsWith("email="))?URLDecoder.decode(q.substring(6),StandardCharsets.UTF_8).toLowerCase().trim():"";
                json(ex,200,m2j(gdb.getOrDefault(em,new LinkedHashMap<>())));
            } else {
                String bd=body(ex);
                String em=jv(bd,"email").toLowerCase().trim();
                if(em.isEmpty()){json(ex,400,"{\"error\":\"Email required\"}");return;}
                Map<String,String> gd=parseObj(bd); gd.remove("email");
                gdb.put(em,gd); save();
                json(ex,200,"{\"ok\":true}");
            }
        } catch(Exception e){json(ex,500,"{\"error\":\"Server error\"}");}
    }

    static void remarks(HttpExchange ex) throws IOException {
        if(cors(ex)) return;
        try {
            if("GET".equals(ex.getRequestMethod())) {
                String q=ex.getRequestURI().getQuery();
                String em=(q!=null&&q.startsWith("email="))?URLDecoder.decode(q.substring(6),StandardCharsets.UTF_8).toLowerCase().trim():"";
                List<Map<String,String>> out=new ArrayList<>();
                for(Map<String,String> r:rdb) if(em.isEmpty()||em.equals(r.getOrDefault("studentEmail","").toLowerCase().trim())) out.add(r);
                json(ex,200,lm2j(out));
            } else {
                Map<String,String> r=parseObj(body(ex));
                r.put("timestamp",new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
                rdb.add(r); save();
                json(ex,200,"{\"ok\":true}");
            }
        } catch(Exception e){json(ex,500,"{\"error\":\"Server error\"}");}
    }

    static void students(HttpExchange ex) throws IOException {
        if(cors(ex)) return;
        try {
            StringBuilder sb=new StringBuilder("["); boolean f=true;
            for(String em:udb.keySet()) {
                Map<String,String> u=udb.get(em);
                if(!"student".equals(u.get("role"))) continue;
                if(!f) sb.append(","); f=false;
                Map<String,String> g=gdb.getOrDefault(em,new LinkedHashMap<>());
                sb.append("{\"email\":\"").append(esc(em)).append("\",\"name\":\"").append(esc(u.get("name"))).append("\",\"grades\":").append(m2j(g)).append("}");
            }
            json(ex,200,sb.append("]").toString());
        } catch(Exception e){json(ex,500,"{\"error\":\"Server error\"}");}
    }

    // html page

    static String html() {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>"
        +"<meta name='viewport' content='width=device-width,initial-scale=1'/>"
        +"<title>Student Performance Engine</title>"
        +"<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>"
        +"<link href='https://fonts.googleapis.com/css2?family=Nunito:wght@400;600;700;800&family=Poppins:wght@600;700;800&display=swap' rel='stylesheet'/>"
        +"<style>"+css()+"</style></head><body>"+authHTML()+appHTML()
        +"<div id='toast'></div><script>"+js()+"</script></body></html>";
    }

    static String css() {
        return ":root{--p:#6C63FF;--pd:#4B44CC;--pl:#A89CFF;--pk:#FF6584;--t:#43C6AC;--o:#FFB347;--r:#FF4757;--g:#2ED573;"
        +"--bg:#F0F2FF;--w:#fff;--br:#E2E8FF;--tx:#2D2D5E;--t2:#6B7DB3;--t3:#A0AABF;font-family:'Nunito',sans-serif;}"
        +"*{margin:0;padding:0;box-sizing:border-box;}body{background:var(--bg);color:var(--tx);}"
        // auth
        +"#auth{display:flex;align-items:center;justify-content:center;min-height:100vh;background:linear-gradient(135deg,#6C63FF,#43C6AC 55%,#FF6584);}"
        +".abox{background:#fff;border-radius:24px;padding:42px 38px;width:420px;max-width:94vw;box-shadow:0 20px 60px rgba(108,99,255,.25);animation:up .5s ease;}"
        +".alogo{text-align:center;margin-bottom:22px;}.alogo .em{font-size:3rem;display:block;}"
        +".alogo h1{font-family:'Poppins',sans-serif;font-size:1.75rem;font-weight:800;background:linear-gradient(135deg,#6C63FF,#43C6AC);-webkit-background-clip:text;-webkit-text-fill-color:transparent;}"
        +".alogo p{color:var(--t2);font-size:.88rem;margin-top:3px;}"
        +".tabs{display:flex;background:var(--bg);border-radius:12px;padding:4px;margin-bottom:22px;}"
        +".tab{flex:1;text-align:center;padding:10px;border-radius:10px;cursor:pointer;font-weight:700;font-size:.88rem;color:var(--t2);transition:.25s;}"
        +".tab.on{background:#fff;color:var(--p);box-shadow:0 2px 10px rgba(108,99,255,.15);}"
        +".fg{margin-bottom:15px;}.fg label{display:block;font-weight:700;font-size:.8rem;color:var(--t2);margin-bottom:5px;text-transform:uppercase;letter-spacing:.4px;}"
        +".fg input,.fg select{width:100%;padding:12px 14px;border:2px solid var(--br);border-radius:12px;font-size:.92rem;font-family:'Nunito',sans-serif;background:var(--bg);color:var(--tx);transition:.25s;}"
        +".fg input:focus,.fg select:focus{outline:none;border-color:var(--p);background:#fff;box-shadow:0 0 0 3px rgba(108,99,255,.1);}"
        +".sbtn{width:100%;padding:13px;background:linear-gradient(135deg,var(--p),var(--pd));color:#fff;border:none;border-radius:12px;font-size:.95rem;font-weight:800;cursor:pointer;font-family:'Nunito',sans-serif;transition:.25s;margin-top:4px;}"
        +".sbtn:hover{transform:translateY(-2px);box-shadow:0 8px 22px rgba(108,99,255,.35);}"
        +".msg{text-align:center;margin-top:12px;font-size:.85rem;min-height:20px;font-weight:600;}"
        +".msg.err{color:var(--r);}.msg.ok{color:var(--g);}"
        // sidebar
        +"#app{display:none;}"
        +".sb{position:fixed;left:0;top:0;bottom:0;width:245px;background:linear-gradient(180deg,#6C63FF,#4B44CC);color:#fff;display:flex;flex-direction:column;z-index:100;box-shadow:4px 0 20px rgba(108,99,255,.3);}"
        +".sbl{padding:24px 20px 16px;border-bottom:1px solid rgba(255,255,255,.15);}"
        +".sbl h2{font-family:'Poppins',sans-serif;font-weight:800;font-size:1.1rem;}"
        +".sbl p{font-size:.75rem;opacity:.65;margin-top:2px;}"
        +".sbu{padding:14px 20px;border-bottom:1px solid rgba(255,255,255,.15);}"
        +".av{width:40px;height:40px;background:rgba(255,255,255,.25);border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:1rem;margin-bottom:7px;}"
        +".un{font-weight:700;font-size:.9rem;}.ur{font-size:.72rem;opacity:.65;background:rgba(255,255,255,.18);display:inline-block;padding:2px 9px;border-radius:20px;margin-top:3px;}"
        +".snav{flex:1;padding:14px 0;overflow-y:auto;}"
        +".nl{display:flex;align-items:center;gap:11px;padding:11px 20px;cursor:pointer;transition:.2s;font-weight:700;font-size:.88rem;opacity:.75;}"
        +".nl:hover{background:rgba(255,255,255,.14);opacity:1;}.nl.on{background:rgba(255,255,255,.2);opacity:1;border-right:3px solid #fff;}"
        +".sbt{padding:14px 20px;}"
        +".lob{width:100%;padding:9px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.28);color:#fff;border-radius:10px;cursor:pointer;font-weight:700;font-size:.82rem;font-family:'Nunito',sans-serif;transition:.2s;}"
        +".lob:hover{background:rgba(255,255,255,.24);}"
        // main
        +".mc{margin-left:245px;padding:28px;min-height:100vh;}"
        +".pg{display:none;}.pg.on{display:block;animation:up .35s ease;}"
        +".ph{margin-bottom:24px;}.ph h1{font-family:'Poppins',sans-serif;font-weight:800;font-size:1.65rem;}"
        +".ph p{color:var(--t2);margin-top:3px;font-size:.9rem;}"
        +".sr{display:grid;grid-template-columns:repeat(auto-fit,minmax(185px,1fr));gap:16px;margin-bottom:24px;}"
        +".sc{background:#fff;border-radius:16px;padding:20px;box-shadow:0 4px 24px rgba(108,99,255,.1);position:relative;overflow:hidden;}"
        +".sc::before{content:'';position:absolute;top:-15px;right:-15px;width:65px;height:65px;border-radius:50%;opacity:.1;}"
        +".sc.c1::before{background:var(--p);}.sc.c2::before{background:var(--pk);}.sc.c3::before{background:var(--t);}.sc.c4::before{background:var(--o);}"
        +".sv{font-family:'Poppins',sans-serif;font-size:1.85rem;font-weight:800;}"
        +".sl{font-size:.76rem;color:var(--t2);font-weight:700;text-transform:uppercase;letter-spacing:.4px;margin-top:3px;}"
        +".sg{position:absolute;top:14px;right:14px;background:var(--bg);border-radius:6px;padding:3px 9px;font-size:.72rem;font-weight:700;color:var(--p);}"
        +".cd{background:#fff;border-radius:16px;padding:24px;box-shadow:0 4px 24px rgba(108,99,255,.1);margin-bottom:20px;}"
        +".ct{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;}"
        +".ct h3{font-family:'Poppins',sans-serif;font-weight:700;font-size:1.02rem;}"
        +".g2{display:grid;grid-template-columns:1fr 1fr;gap:20px;}"
        +".tb{width:100%;border-collapse:collapse;}"
        +".tb th{padding:10px 13px;text-align:left;font-size:.76rem;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:var(--t2);background:var(--bg);}"
        +".tb td{padding:12px 13px;border-bottom:1px solid var(--br);font-size:.87rem;}"
        +".tb tr:hover td{background:var(--bg);}"
        +".tg{display:inline-block;padding:3px 10px;border-radius:20px;font-size:.72rem;font-weight:700;}"
        +".tg{display:inline-block;padding:3px 10px;border-radius:20px;font-size:.72rem;font-weight:700;}"
        +".gr{background:#D4F5E4;color:#1A7A4A;}.yw{background:#FFF3D4;color:#7A5A00;}.rd{background:#FFE4E7;color:#8B0000;}.bl{background:#E0E7FF;color:var(--pd);}"
        +".inf{padding:10px 13px;border:2px solid var(--br);border-radius:10px;font-size:.87rem;font-family:'Nunito',sans-serif;background:var(--bg);color:var(--tx);width:100%;transition:.25s;}"
        +".inf:focus{outline:none;border-color:var(--p);background:#fff;}textarea.inf{resize:vertical;min-height:85px;}"
        +".fl{display:block;font-weight:700;font-size:.78rem;color:var(--t2);margin-bottom:5px;text-transform:uppercase;letter-spacing:.4px;}"
        +".btn{padding:9px 17px;border-radius:10px;border:none;font-weight:700;cursor:pointer;font-family:'Nunito',sans-serif;font-size:.84rem;transition:.25s;}"
        +".bsm{padding:6px 12px;font-size:.77rem;border-radius:7px;}"
        +".bp{background:linear-gradient(135deg,var(--p),var(--pd));color:#fff;}.bp:hover{transform:translateY(-1px);box-shadow:0 5px 15px rgba(108,99,255,.3);}"
        +".bt{background:linear-gradient(135deg,var(--t),#2DA58E);color:#fff;}.bt:hover{transform:translateY(-1px);}"
        +".bk{background:linear-gradient(135deg,var(--pk),#D94F6A);color:#fff;}"
        +".bo{background:#fff;color:var(--p);border:2px solid var(--p);}.bo:hover{background:var(--p);color:#fff;}"
        +".subg{display:grid;grid-template-columns:repeat(auto-fit,minmax(165px,1fr));gap:13px;margin-bottom:16px;}"
        +".subc{background:var(--bg);border-radius:13px;padding:15px;border:2px solid var(--br);transition:.25s;}"
        +".subc:focus-within{border-color:var(--p);background:#fff;}"
        +".subn{font-weight:800;font-size:.84rem;margin-bottom:8px;}"
        +".subc input{width:100%;padding:8px;border:2px solid var(--br);border-radius:7px;font-size:1.02rem;font-weight:800;text-align:center;background:#fff;color:var(--tx);font-family:'Poppins',sans-serif;transition:.2s;}"
        +".subc input:focus{outline:none;border-color:var(--p);}"
        +".gl{display:inline-block;margin-top:6px;padding:2px 8px;border-radius:20px;font-size:.72rem;font-weight:700;}"
        +".prb{height:9px;background:var(--br);border-radius:10px;overflow:hidden;margin-top:5px;}"
        +".prf{height:100%;border-radius:10px;transition:width .7s ease;}"
        +".ins{display:flex;gap:12px;padding:13px;background:var(--bg);border-radius:12px;margin-bottom:10px;}"
        +".ini{font-size:1.3rem;flex-shrink:0;width:40px;height:40px;background:#fff;border-radius:9px;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 24px rgba(108,99,255,.1);}"
        +".int h4{font-weight:800;font-size:.87rem;margin-bottom:2px;}.int p{font-size:.79rem;color:var(--t2);}"
        +".bdg{display:grid;grid-template-columns:repeat(auto-fit,minmax(108px,1fr));gap:13px;}"
        +".bc{background:#fff;border-radius:13px;padding:15px;text-align:center;box-shadow:0 4px 24px rgba(108,99,255,.1);border:2px solid var(--br);}"
        +".bc.e{border-color:var(--o);background:linear-gradient(135deg,#FFF9E6,#FFF3CC);}"
        +".be{font-size:1.75rem;display:block;margin-bottom:6px;}.bn{font-weight:800;font-size:.77rem;}.bd{font-size:.69rem;color:var(--t2);margin-top:2px;}"
        +".pod{display:flex;align-items:flex-end;justify-content:center;gap:13px;margin:16px 0;}"
        +".pi{text-align:center;flex:1;max-width:115px;}"
        +".pb{border-radius:9px 9px 0 0;display:flex;align-items:center;justify-content:center;font-size:1.35rem;color:#fff;font-weight:900;}"
        +".pb.r1{background:linear-gradient(180deg,#FFD700,#FFA500);height:95px;}.pb.r2{background:linear-gradient(180deg,#C0C0C0,#888);height:68px;}.pb.r3{background:linear-gradient(180deg,#CD7F32,#8B4513);height:52px;}"
        +".pn{font-weight:800;font-size:.78rem;margin-top:5px;}.ps{font-size:.73rem;color:var(--t2);}"
        +".ri{display:flex;align-items:center;gap:11px;padding:10px 13px;background:linear-gradient(135deg,#FFE4E7,#FFD0D6);border-radius:11px;margin-bottom:8px;}"
        +".rd2{width:9px;height:9px;background:var(--r);border-radius:50%;flex-shrink:0;}"
        +".rem{padding:13px;background:var(--bg);border-radius:11px;margin-bottom:10px;border-left:4px solid var(--p);}"
        +".rem.pr{border-left-color:var(--g);}.rem.wa{border-left-color:var(--o);}.rem.at{border-left-color:var(--pk);}"
        +".rh{display:flex;justify-content:space-between;margin-bottom:4px;}"
        +".rt{font-size:.72rem;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:var(--p);}"
        +".rd3{font-size:.72rem;color:var(--t3);}.rb{font-size:.84rem;color:var(--t2);}"
        +".atg{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin-bottom:16px;}"
        +".atb{padding:10px;border-radius:11px;border:2px solid var(--br);cursor:pointer;text-align:center;font-weight:700;font-size:.79rem;background:#fff;transition:.2s;}"
        +".atb:hover,.atb.on{border-color:var(--p);background:var(--bg);color:var(--p);}"
        +".cw{position:relative;height:260px;}"
        +".tt{font-size:.72rem;padding:3px 8px;border-radius:20px;font-weight:700;display:inline-block;margin-top:5px;}"
        +".tok{background:#D4F5E4;color:#1A7A4A;}.tno{background:#FFE4E7;color:#8B0000;}"
        +"#toast{position:fixed;bottom:26px;right:26px;padding:12px 20px;border-radius:11px;color:#fff;font-weight:700;z-index:9999;opacity:0;transition:.35s;pointer-events:none;font-size:.87rem;max-width:290px;}"
        +"#toast.show{opacity:1;}#toast.s{background:linear-gradient(135deg,var(--g),#1A8A50);}#toast.e{background:linear-gradient(135deg,var(--r),#990022);}#toast.i{background:linear-gradient(135deg,var(--p),var(--pd));}"
        +"@keyframes up{from{opacity:0;transform:translateY(16px);}to{opacity:1;transform:translateY(0);}}"
        +"::-webkit-scrollbar{width:5px;}::-webkit-scrollbar-thumb{background:var(--pl);border-radius:10px;}"
        +"@media(max-width:760px){.sb{width:52px;}.sbl p,.un,.ur,.nl span{display:none;}.mc{margin-left:52px;padding:13px;}.g2{grid-template-columns:1fr;}.sr{grid-template-columns:1fr 1fr;}}"
        +"@media print{.sb,.np{display:none!important;}.mc{margin-left:0;padding:10px;}body{background:#fff;}.cd{box-shadow:none;border:1px solid #ddd;}}";
    }

    static String authHTML() {
        return "<div id='auth'><div class='abox'>"
        +"<div class='alogo'><span class='em'>&#127890;</span><h1>Student Performance Engine</h1><p>College Project</p></div>"
        +"<div class='tabs'><div class='tab on' id='tli' onclick='swTab(\"li\")'>Sign In</div><div class='tab' id='tre' onclick='swTab(\"re\")'>Register</div></div>"
        +"<div id='liF'>"
        +"<div class='fg'><label>Email</label><input type='email' id='liE' placeholder='yourname@gmail.com'/></div>"
        +"<div class='fg'><label>Password</label><input type='password' id='liP' placeholder='Enter password'/></div>"
        +"<button class='sbtn' id='liB' onclick='doLogin()'>Sign In</button>"
        +"<div class='msg' id='liM'></div></div>"
        +"<div id='reF' style='display:none'>"
        +"<div class='fg'><label>Full Name</label><input type='text' id='reN' placeholder='Your full name'/></div>"
        +"<div class='fg'><label>Email</label><input type='email' id='reE' placeholder='yourname@gmail.com'/></div>"
        +"<div class='fg'><label>Password</label><input type='password' id='reP' placeholder='Min 6 characters'/></div>"
        +"<div class='fg'><label>I am a</label><select id='reR'><option value='student'>Student</option><option value='teacher'>Teacher</option></select></div>"
        +"<button class='sbtn' id='reB' onclick='doReg()'>Create Account</button>"
        +"<div class='msg' id='reM'></div></div>"
        +"</div></div>";
    }

    static String appHTML() {
        return "<div id='app'>"
        +"<nav class='sb'>"
        +"<div class='sbl'><h2>&#127890; SPE</h2><p>Performance Engine</p></div>"
        +"<div class='sbu'><div class='av' id='av'>?</div><div class='un' id='un'>User</div><div class='ur' id='ur'>role</div></div>"
        +"<div class='snav' id='snav'></div>"
        +"<div class='sbt'><button class='lob' onclick='out()'>&larr; Sign Out</button></div>"
        +"</nav>"
        +"<div class='mc'>"
        // student pages
        +"<div class='pg' id='pg-sd'><div class='ph'><h1>&#128202; Dashboard</h1><p>Your performance overview</p></div>"
        +"<div class='sr' id='sd-s'></div>"
        +"<div class='g2'><div class='cd'><div class='ct'><h3>Subject Scores</h3></div><div class='cw'><canvas id='sB'></canvas></div></div>"
        +"<div class='cd'><div class='ct'><h3>Skill Radar</h3></div><div class='cw'><canvas id='sR'></canvas></div></div></div>"
        +"<div class='g2'><div class='cd'><div class='ct'><h3>&#129504; AI Insights</h3></div><div id='sd-i'></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#127942; Badges</h3></div><div class='bdg' id='sd-b'></div></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#128226; Teacher Remarks</h3></div><div id='sd-r'></div></div></div>"

        +"<div class='pg' id='pg-sg'><div class='ph'><h1>&#9999;&#65039; My Grades</h1><p>Enter your scores</p></div>"
        +"<div class='cd'><div class='ct'><h3>Current Scores (out of 100)</h3><button class='btn bp' onclick='saveG()'>&#128190; Save</button></div>"
        +"<div class='subg' id='gi'></div>"
        +"<div class='ct' style='margin-top:16px'><h3>&#127919; Target Scores</h3></div>"
        +"<div class='subg' id='ti'></div>"
        +"<button class='btn bt' style='margin-top:8px' onclick='saveG()'>&#128190; Save All</button></div></div>"

        +"<div class='pg' id='pg-sa'><div class='ph'><h1>&#128269; Analysis</h1><p>Detailed breakdown</p></div>"
        +"<div class='cd'><div class='ct'><h3>Subject Breakdown</h3></div><div id='sa-b'></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#129504; Study Tips</h3></div><div id='sa-t'></div></div></div>"

        +"<div class='pg' id='pg-sr'><div class='ph'><h1>&#128196; Report Card</h1><p>Your performance report</p></div>"
        +"<div class='cd' id='sr-c'></div>"
        +"<button class='btn bp np' style='margin-top:13px' onclick='window.print()'>&#128424; Print / Save PDF</button></div>"

        // teacher pages
        +"<div class='pg' id='pg-to'><div class='ph'><h1>&#9641; Class Overview</h1><p>Live class stats</p></div>"
        +"<div class='sr' id='to-s'></div>"
        +"<div class='g2'><div class='cd'><div class='ct'><h3>Subject Averages</h3></div><div class='cw'><canvas id='tSA'></canvas></div></div>"
        +"<div class='cd'><div class='ct'><h3>Distribution</h3></div><div class='cw'><canvas id='tD'></canvas></div></div></div>"
        +"<div class='g2'><div class='cd'><div class='ct'><h3>&#127941; Top Performers</h3></div><div class='pod' id='tpod'></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#9888;&#65039; At-Risk</h3></div><div id='tar'></div></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#128101; All Students</h3><span class='tg bl' id='tsc'>0</span></div>"
        +"<div style='overflow-x:auto'><table class='tb'><thead><tr><th>Name</th><th>Email</th><th>Average</th><th>Status</th><th>Action</th></tr></thead><tbody id='ttb'></tbody></table></div></div></div>"

        +"<div class='pg' id='pg-te'><div class='ph'><h1>&#9999;&#65039; Edit Grades</h1><p>Update student scores</p></div>"
        +"<div class='cd' style='margin-bottom:16px'><label class='fl'>Select Student</label>"
        +"<select class='inf' id='tes' style='max-width:350px' onchange='loadEP()'></select></div>"
        +"<div id='tep' style='display:none'>"
        +"<div class='cd'><div class='ct'><h3 id='ten'>Student</h3><button class='btn bp' onclick='saveTG()'>&#128190; Save</button></div>"
        +"<div class='subg' id='teg'></div></div>"
        +"<div class='g2'><div class='cd'><div class='ct'><h3>Bar Chart</h3></div><div class='cw'><canvas id='teB'></canvas></div></div>"
        +"<div class='cd'><div class='ct'><h3>Radar</h3></div><div class='cw'><canvas id='teR'></canvas></div></div></div>"
        +"<div class='cd'><div class='ct'><h3>&#128226; Remarks</h3><button class='btn bo bsm' onclick='prSR()'>&#128424; Print</button></div>"
        +"<div id='ter'></div></div></div></div>"

        +"<div class='pg' id='pg-ta'><div class='ph'><h1>&#128226; Alerts</h1><p>Send remarks to students</p></div>"
        +"<div class='g2'>"
        +"<div class='cd'><div style='margin-bottom:13px'><label class='fl'>Student</label><select class='inf' id='als' onchange='loadAR()'></select></div>"
        +"<div style='margin-bottom:13px'><label class='fl'>Alert Type</label>"
        +"<div class='atg'><div class='atb on' data-t='Praise' onclick='pickT(this)'>&#127775; Praise</div>"
        +"<div class='atb' data-t='Performance Warning' onclick='pickT(this)'>&#9888; Warning</div>"
        +"<div class='atb' data-t='Attendance Alert' onclick='pickT(this)'>&#128197; Attend.</div>"
        +"<div class='atb' data-t='Custom' onclick='pickT(this)'>&#9999; Custom</div></div></div>"
        +"<div style='margin-bottom:13px'><label class='fl'>Message</label><textarea class='inf' id='alm' rows='4' placeholder='Write message here...'></textarea></div>"
        +"<div style='display:flex;gap:9px'><button class='btn bo' onclick='autoR()'>&#10024; Auto-Generate</button>"
        +"<button class='btn bp' onclick='sendR()'>&#128228; Send</button></div>"
        +"<div id='als2' style='margin-top:9px;font-weight:700;font-size:.84rem'></div></div>"
        +"<div class='cd'><div class='ct'><h3>Session Log</h3><span class='tg bl' id='rsc'>0 sent</span></div>"
        +"<div id='slog' style='max-height:400px;overflow-y:auto'></div></div></div></div>"

        +"<div class='pg' id='pg-tr'><div class='ph'><h1>&#128196; Class Report</h1><p>Full class report</p></div>"
        +"<div style='display:flex;gap:11px;margin-bottom:20px' class='np'>"
        +"<button class='btn bp' onclick='genCR()'>&#128202; Generate</button>"
        +"<button class='btn bt' onclick='prCR()'>&#128424; Print / PDF</button></div>"
        +"<div id='tr-c'></div></div>"
        +"</div></div>";
    }

    static String js() {
        return
"var subs=['Mathematics','Biology','English','History','Geography','Computer Science','Physics','Chemistry'];\n"
+"var cu=null,mg={},as=[],sBC=null,sRC=null,tSAC=null,tDC=null,tBC=null,tRC=null,rsc=0,cat='Praise';\n"

+"function swTab(t){\n"
+"  document.getElementById('tli').classList.toggle('on',t==='li');\n"
+"  document.getElementById('tre').classList.toggle('on',t==='re');\n"
+"  document.getElementById('liF').style.display=t==='li'?'block':'none';\n"
+"  document.getElementById('reF').style.display=t==='re'?'block':'none';\n"
+"}\n"

+"function msg(id,txt,tp){var e=document.getElementById(id);e.textContent=txt;e.className='msg'+(tp?' '+tp:'');}\n"

+"async function doLogin(){\n"
+"  var em=document.getElementById('liE').value.trim(),pw=document.getElementById('liP').value;\n"
+"  if(!em||!pw){msg('liM','Fill all fields','err');return;}\n"
+"  msg('liM','Signing in...',''); document.getElementById('liB').disabled=true;\n"
+"  try{\n"
+"    var r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:em,password:pw})});\n"
+"    var d=await r.json();\n"
+"    if(d.ok){cu={email:em.toLowerCase(),name:d.name,role:d.role};msg('liM','Welcome '+d.name,'ok');setTimeout(boot,600);}\n"
+"    else{msg('liM',d.error||'Login failed','err');document.getElementById('liB').disabled=false;}\n"
+"  }catch(e){msg('liM','Server not running?','err');document.getElementById('liB').disabled=false;}\n"
+"}\n"

+"async function doReg(){\n"
+"  var nm=document.getElementById('reN').value.trim(),em=document.getElementById('reE').value.trim();\n"
+"  var pw=document.getElementById('reP').value,rl=document.getElementById('reR').value;\n"
+"  if(!nm||!em||!pw){msg('reM','Fill all fields','err');return;}\n"
+"  if(pw.length<6){msg('reM','Password min 6 chars','err');return;}\n"
+"  if(!em.includes('@')){msg('reM','Enter valid email','err');return;}\n"
+"  msg('reM','Creating account...',''); document.getElementById('reB').disabled=true;\n"
+"  try{\n"
+"    var r=await fetch('/api/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:nm,email:em,password:pw,role:rl})});\n"
+"    var d=await r.json();\n"
+"    if(d.ok){cu={email:em.toLowerCase(),name:nm,role:rl};msg('reM','Done! Logging in...','ok');setTimeout(boot,700);}\n"
+"    else{msg('reM',d.error||'Failed','err');document.getElementById('reB').disabled=false;}\n"
+"  }catch(e){msg('reM','Server not running?','err');document.getElementById('reB').disabled=false;}\n"
+"}\n"

+"function out(){\n"
+"  cu=null;mg={};as=[];\n"
+"  document.getElementById('auth').style.display='flex';\n"
+"  document.getElementById('app').style.display='none';\n"
+"  document.getElementById('liE').value='';document.getElementById('liP').value='';\n"
+"  document.getElementById('liB').disabled=false;msg('liM','','');\n"
+"}\n"

+"async function boot(){\n"
+"  document.getElementById('auth').style.display='none';\n"
+"  document.getElementById('app').style.display='block';\n"
+"  document.getElementById('av').textContent=cu.name.charAt(0).toUpperCase();\n"
+"  document.getElementById('un').textContent=cu.name;\n"
+"  document.getElementById('ur').textContent=cu.role;\n"
+"  buildNav();\n"
+"  if(cu.role==='student'){await loadMG();showPg('sd');}else{await loadAS();showPg('to');}\n"
+"}\n"

+"function buildNav(){\n"
+"  var items=cu.role==='student'?\n"
+"    [{id:'sd',ic:'&#128202;',lb:'Dashboard'},{id:'sg',ic:'&#9999;',lb:'My Grades'},{id:'sa',ic:'&#128269;',lb:'Analysis'},{id:'sr',ic:'&#128196;',lb:'Report Card'}]:\n"
+"    [{id:'to',ic:'&#9641;',lb:'Overview'},{id:'te',ic:'&#9999;',lb:'Edit Grades'},{id:'ta',ic:'&#128226;',lb:'Alerts'},{id:'tr',ic:'&#128196;',lb:'Class Report'}];\n"
+"  var h=''; for(var i=0;i<items.length;i++) h+='<div class=\"nl\" id=\"nl-'+items[i].id+'\" onclick=\"showPg(\\''+items[i].id+'\\')\">'+'<span>'+items[i].ic+'</span><span>'+items[i].lb+'</span></div>';\n"
+"  document.getElementById('snav').innerHTML=h;\n"
+"}\n"

+"function showPg(id){\n"
+"  document.querySelectorAll('.pg').forEach(function(p){p.classList.remove('on');});\n"
+"  document.querySelectorAll('.nl').forEach(function(n){n.classList.remove('on');});\n"
+"  var pg=document.getElementById('pg-'+id),nl=document.getElementById('nl-'+id);\n"
+"  if(pg)pg.classList.add('on'); if(nl)nl.classList.add('on');\n"
+"  if(id==='sd')renderDash(); if(id==='sg')renderGI(); if(id==='sa')renderAna();\n"
+"  if(id==='sr')renderRC();   if(id==='to')renderTO(); if(id==='te'||id==='ta')fillDD();\n"
+"}\n"

+"async function loadMG(){try{var r=await fetch('/api/grades?email='+encodeURIComponent(cu.email));mg=await r.json();}catch(e){mg={};}}\n"
+"async function loadAS(){try{var r=await fetch('/api/students');as=await r.json();}catch(e){as=[];}}\n"
+"async function getRem(em){try{var r=await fetch('/api/remarks'+(em?'?email='+encodeURIComponent(em):''));return await r.json();}catch(e){return[];}}\n"

// helpers
+"function gv(s){var v=parseFloat(mg['g_'+s])||0;return v;}\n"
+"function gc(v){v=parseFloat(v)||0;if(v>=80)return'#2ED573';if(v>=60)return'#FFB347';if(v>0)return'#FF4757';return'#A0AABF';}\n"
+"function gl(v){v=parseFloat(v)||0;if(v>=90)return'A+';if(v>=80)return'A';if(v>=70)return'B';if(v>=60)return'C';if(v>=50)return'D';return v>0?'F':'&mdash;';}\n"
+"function toast(m,t){var e=document.getElementById('toast');e.innerHTML=m;e.className='show '+t;setTimeout(function(){e.className='';},3000);}\n"
+"function remCard(r){var cl=r.type==='Praise'?'pr':r.type&&r.type.includes('Warning')?'wa':'at';return'<div class=\"rem '+cl+'\"><div class=\"rh\"><span class=\"rt\">'+r.type+'</span><span class=\"rd3\">'+(r.timestamp||'')+'</span></div><div style=\"font-size:.79rem;color:var(--t2);margin-bottom:3px;\">By: '+(r.teacherName||'Teacher')+'</div><div class=\"rb\">'+r.message+'</div></div>';}\n"

// dashboard
+"async function renderDash(){\n"
+"  await loadMG();\n"
+"  var sc=[],wk=0,best=0;\n"
+"  for(var i=0;i<subs.length;i++){var v=gv(subs[i]);if(v>0){sc.push(v);if(v<60)wk++;if(v>best)best=v;}}\n"
+"  var avg=sc.length?(sc.reduce(function(a,b){return a+b;},0)/sc.length).toFixed(1):0;\n"
+"  document.getElementById('sd-s').innerHTML='<div class=\"sc c1\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#128202;</div><div class=\"sv\">'+avg+'%</div><div class=\"sl\">Overall Avg</div><div class=\"sg\">'+gl(avg)+'</div></div>'\n"
+"    +'<div class=\"sc c2\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#9888;</div><div class=\"sv\">'+wk+'</div><div class=\"sl\">Weak Subjects</div></div>'\n"
+"    +'<div class=\"sc c3\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#127942;</div><div class=\"sv\">'+best+'%</div><div class=\"sl\">Best Score</div></div>'\n"
+"    +'<div class=\"sc c4\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#128218;</div><div class=\"sv\">'+sc.length+'/'+subs.length+'</div><div class=\"sl\">Subjects Done</div></div>';\n"
+"  var lb=[],vs=[],cs=[];\n"
+"  for(var i=0;i<subs.length;i++){var v=gv(subs[i]);if(v>0){lb.push(subs[i]);vs.push(v);cs.push(v>=80?'rgba(46,213,115,.8)':v>=60?'rgba(255,179,71,.8)':'rgba(255,71,87,.8)');}}\n"
+"  if(sBC)sBC.destroy();\n"
+"  sBC=new Chart(document.getElementById('sB'),{type:'bar',data:{labels:lb,datasets:[{label:'Score',data:vs,backgroundColor:cs,borderRadius:7,borderSkipped:false}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{y:{min:0,max:100}}}});\n"
+"  if(sRC)sRC.destroy();\n"
+"  sRC=new Chart(document.getElementById('sR'),{type:'radar',data:{labels:subs,datasets:[{label:'Score',data:subs.map(function(s){return gv(s);}),backgroundColor:'rgba(108,99,255,.2)',borderColor:'#6C63FF',pointBackgroundColor:'#6C63FF',borderWidth:2}]},options:{responsive:true,maintainAspectRatio:false,scales:{r:{min:0,max:100,ticks:{stepSize:25}}}}});\n"
+"  var ins=buildIns();\n"
+"  document.getElementById('sd-i').innerHTML=ins.map(function(i){return'<div class=\"ins\"><div class=\"ini\">'+i.ic+'</div><div class=\"int\"><h4>'+i.t+'</h4><p>'+i.d+'</p></div></div>';}).join('');\n"
+"  document.getElementById('sd-b').innerHTML=buildBdg().map(function(b){return'<div class=\"bc '+(b.e?'e':'')+'\"><span class=\"be\">'+(b.e?b.ic:'&#128274;')+'</span><div class=\"bn\">'+b.n+'</div><div class=\"bd\">'+b.d+'</div></div>';}).join('');\n"
+"  var rems=await getRem(cu.email);\n"
+"  document.getElementById('sd-r').innerHTML=rems.length?rems.slice().reverse().map(remCard).join(''):'<p style=\"color:var(--t3);text-align:center;padding:16px\">No remarks yet.</p>';\n"
+"}\n"

+"function buildIns(){\n"
+"  var lst=[];for(var i=0;i<subs.length;i++){var v=gv(subs[i]);if(v>0)lst.push({s:subs[i],v:v});}\n"
+"  if(!lst.length)return[{ic:'&#128161;',t:'No data',d:'Enter your grades to see insights.'}];\n"
+"  var ins=[];\n"
+"  lst.sort(function(a,b){return a.v-b.v;});\n"
+"  if(lst[0].v<60)ins.push({ic:'&#9888;',t:'Focus: '+lst[0].s,d:lst[0].s+' is '+lst[0].v+'%. Practise past papers daily.'});\n"
+"  lst.sort(function(a,b){return b.v-a.v;});\n"
+"  ins.push({ic:'&#127775;',t:'Strength: '+lst[0].s,d:'Great '+lst[0].v+'% in '+lst[0].s+'. Keep it up!'});\n"
+"  var avg=lst.reduce(function(a,b){return a+b.v;},0)/lst.length;\n"
+"  ins.push(avg>=80?{ic:'&#128640;',t:'Outstanding!',d:'Average '+avg.toFixed(1)+'%. Excellent work!'}:avg>=60?{ic:'&#128200;',t:'Good Progress',d:'Average '+avg.toFixed(1)+'%. Keep revising!'}:{ic:'&#128218;',t:'Needs Effort',d:'Average '+avg.toFixed(1)+'%. Study daily and ask for help.'});\n"
+"  var wk=lst.filter(function(x){return x.v<70;});\n"
+"  if(wk.length)ins.push({ic:'&#127919;',t:'Quick Win',d:'Watch Khan Academy on '+wk[0].s+' to boost scores fast.'});\n"
+"  return ins;\n"
+"}\n"

+"function buildBdg(){\n"
+"  var sc=[];for(var i=0;i<subs.length;i++){var v=gv(subs[i]);if(v>0)sc.push(v);}\n"
+"  var avg=sc.length?sc.reduce(function(a,b){return a+b;},0)/sc.length:0;\n"
+"  var ht=false;for(var i=0;i<subs.length;i++)if(parseFloat(mg['t_'+subs[i]])>0){ht=true;break;}\n"
+"  return[\n"
+"    {ic:'&#127775;',n:'All-Rounder',d:'Avg >= 75%',e:avg>=75},\n"
+"    {ic:'&#128293;',n:'Top Scorer', d:'Avg >= 90%',e:avg>=90},\n"
+"    {ic:'&#128218;',n:'Diligent',   d:'All subjects',e:sc.length>=8},\n"
+"    {ic:'&#128170;',n:'Improver',   d:'No score < 50%',e:sc.length>0&&Math.min.apply(null,sc)>=50},\n"
+"    {ic:'&#127942;',n:'Champion',   d:'Any score 100%',e:sc.indexOf(100)>=0},\n"
+"    {ic:'&#127919;',n:'Goal Setter',d:'Targets set',e:ht}\n"
+"  ];\n"
+"}\n"

// grades input
+"function renderGI(){\n"
+"  var gh='',th='';\n"
+"  for(var i=0;i<subs.length;i++){\n"
+"    var s=subs[i],k=s.replace(/ /g,'-'),val=mg['g_'+s]||'',col=gc(parseFloat(val));\n"
+"    gh+='<div class=\"subc\"><div class=\"subn\">'+s+'</div><input type=\"number\" min=\"0\" max=\"100\" placeholder=\"0-100\" value=\"'+val+'\" id=\"gi-'+k+'\" oninput=\"upP(\\''+s+'\\')\" /><div id=\"gp-'+k+'\" class=\"gl\" style=\"background:'+col+'22;color:'+col+'\">'+( val?gl(parseFloat(val)):'&mdash;')+'</div></div>';\n"
+"    var tv=mg['t_'+s]||'',cv=parseFloat(mg['g_'+s])||0,tgv=parseFloat(tv)||0,ok=!tgv||!cv||cv>=tgv;\n"
+"    th+='<div class=\"subc\"><div class=\"subn\">&#127919; '+s+'</div><input type=\"number\" min=\"0\" max=\"100\" placeholder=\"Target\" value=\"'+tv+'\" id=\"ti-'+k+'\"/><div class=\"tt '+(ok?'tok':'tno')+'\">'+(tgv?(ok?'&#10003; On Track':'&#10007; Below'):'&mdash;')+'</div></div>';\n"
+"  }\n"
+"  document.getElementById('gi').innerHTML=gh;\n"
+"  document.getElementById('ti').innerHTML=th;\n"
+"}\n"

+"function upP(s){\n"
+"  var k=s.replace(/ /g,'-'),el=document.getElementById('gi-'+k),pill=document.getElementById('gp-'+k);\n"
+"  if(!el||!pill)return;\n"
+"  var v=parseFloat(el.value)||0,col=gc(v);\n"
+"  pill.textContent=v?gl(v):'—'; pill.style.background=col+'22'; pill.style.color=col;\n"
+"}\n"

+"async function saveG(){\n"
+"  var data={email:cu.email};\n"
+"  for(var i=0;i<subs.length;i++){\n"
+"    var s=subs[i],k=s.replace(/ /g,'-');\n"
+"    var gi=document.getElementById('gi-'+k),ti=document.getElementById('ti-'+k);\n"
+"    if(gi)data['g_'+s]=gi.value||''; if(ti)data['t_'+s]=ti.value||'';\n"
+"  }\n"
+"  try{\n"
+"    var r=await fetch('/api/grades',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});\n"
+"    var d=await r.json();\n"
+"    if(d.ok){mg=data;toast('Grades saved!','s');}else toast('Error saving','e');\n"
+"  }catch(e){toast('Server error','e');}\n"
+"}\n"

// analysis
+"function renderAna(){\n"
+"  var bh='';\n"
+"  for(var i=0;i<subs.length;i++){\n"
+"    var s=subs[i],v=parseFloat(mg['g_'+s])||0,t=parseFloat(mg['t_'+s])||0,col=gc(v),diff=t&&v?v-t:0;\n"
+"    bh+='<div style=\"margin-bottom:16px\"><div style=\"display:flex;justify-content:space-between;align-items:center;margin-bottom:4px\"><span style=\"font-weight:800\">'+s+'</span><div style=\"display:flex;gap:8px;align-items:center\">'+(t?'<span style=\"font-size:.75rem;color:var(--t2)\">Target:'+t+'%</span>':'')+'<span style=\"font-weight:800;color:'+col+'\">'+(v?v+'%':'&mdash;')+'</span><span class=\"tg '+(v>=80?'gr':v>=60?'yw':'rd')+'\">'+gl(v)+'</span></div></div><div class=\"prb\"><div class=\"prf\" style=\"width:'+v+'%;background:'+col+'\"></div></div>'+(t&&v?'<div style=\"font-size:.75rem;margin-top:3px;color:'+(diff>=0?'var(--g)':'var(--r)')+'\">'+(diff>=0?'&#10003; '+diff.toFixed(1)+'% above':'&#10007; '+Math.abs(diff).toFixed(1)+'% below')+' target</div>':'')+'</div>';\n"
+"  }\n"
+"  document.getElementById('sa-b').innerHTML=bh;\n"
+"  var tips={'Mathematics':'Solve 10 problems daily. Use NCERT + Khan Academy.','Biology':'Draw diagrams for each chapter. Make flashcards for key processes.','English':'Read one article daily. Practice grammar and essay writing.','History':'Make timelines. Use mnemonics for dates and events.','Geography':'Draw and label maps. Watch documentaries.','Computer Science':'Code daily on HackerRank or LeetCode.','Physics':'Understand formulas deeply. Solve 5 numericals daily.','Chemistry':'Master the periodic table. Practice balancing equations.'};\n"
+"  var th='';\n"
+"  for(var i=0;i<subs.length;i++){\n"
+"    var s=subs[i],v=parseFloat(mg['g_'+s])||0,iw=v>0&&v<70;\n"
+"    var bc=iw?'var(--r)':v>=80?'var(--g)':v>0?'var(--o)':'var(--br)';\n"
+"    var tag=iw?'<span style=\"background:#FFE4E7;color:#8B0000;font-size:.71rem;font-weight:700;padding:2px 8px;border-radius:20px;margin-left:7px\">Needs Focus</span>':v>=80?'<span style=\"background:#D4F5E4;color:#1A7A4A;font-size:.71rem;font-weight:700;padding:2px 8px;border-radius:20px;margin-left:7px\">Strong</span>':'';\n"
+"    th+='<div style=\"padding:15px;background:var(--bg);border-radius:12px;margin-bottom:11px;border-left:4px solid '+bc+'\"><h4 style=\"font-weight:800;font-size:.91rem;margin-bottom:5px\">'+s+(v?' &mdash; '+v+'%':'')+tag+'</h4><p style=\"font-size:.83rem;color:var(--t2)\">'+tips[s]+'</p></div>';\n"
+"  }\n"
+"  document.getElementById('sa-t').innerHTML=th;\n"
+"}\n"

// report card
+"async function renderRC(){\n"
+"  await loadMG();\n"
+"  var rems=await getRem(cu.email);\n"
+"  var sc=[];for(var i=0;i<subs.length;i++){var v=gv(subs[i]);if(v>0)sc.push(v);}\n"
+"  var avg=sc.length?(sc.reduce(function(a,b){return a+b;},0)/sc.length).toFixed(1):0;\n"
+"  var today=new Date().toLocaleDateString('en-IN',{day:'2-digit',month:'long',year:'numeric'});\n"
+"  var h='<div style=\"text-align:center;padding:16px 0 24px;border-bottom:2px solid var(--br)\"><div style=\"font-size:2.4rem\">&#127890;</div><h2 style=\"font-family:Poppins,sans-serif;font-size:1.45rem;font-weight:800;color:var(--p);margin:7px 0 3px\">Student Performance Report Card</h2><p style=\"color:var(--t2)\">College Project</p><p style=\"color:var(--t3);font-size:.79rem\">Date: '+today+'</p></div>';\n"
+"  h+='<div style=\"display:grid;grid-template-columns:1fr 1fr;gap:13px;padding:16px 0;border-bottom:1px solid var(--br)\"><div><span style=\"color:var(--t2);font-size:.79rem\">Name</span><br><strong>'+cu.name+'</strong></div><div><span style=\"color:var(--t2);font-size:.79rem\">Email</span><br><strong>'+cu.email+'</strong></div><div><span style=\"color:var(--t2);font-size:.79rem\">Overall Average</span><br><strong style=\"color:var(--p);font-size:1.2rem\">'+avg+'%</strong></div><div><span style=\"color:var(--t2);font-size:.79rem\">Grade</span><br><strong style=\"font-size:1.2rem;color:'+gc(avg)+'\">'+gl(avg)+'</strong></div></div>';\n"
+"  h+='<table class=\"tb\" style=\"margin-top:16px\"><thead><tr><th>Subject</th><th>Score</th><th>Grade</th><th>Status</th><th>Target</th></tr></thead><tbody>';\n"
+"  for(var i=0;i<subs.length;i++){var s=subs[i],v=parseFloat(mg['g_'+s])||0,t=parseFloat(mg['t_'+s])||0;h+='<tr><td>'+s+'</td><td style=\"font-weight:700\">'+(v||'&mdash;')+'</td><td><span class=\"tg '+(v>=80?'gr':v>=60?'yw':'rd')+'\">'+gl(v)+'</span></td><td>'+(v>=80?'Excellent':v>=60?'Good':v>=40?'Average':v>0?'Needs Help':'Not Submitted')+'</td><td>'+(t?t+'%':'&mdash;')+'</td></tr>';}\n"
+"  h+='</tbody></table>'+(rems.length?'<div style=\"margin-top:20px\"><h4 style=\"font-weight:800;margin-bottom:10px\">&#128226; Teacher Remarks</h4>'+rems.map(remCard).join('')+'</div>':'')+'<div style=\"text-align:center;padding:16px 0 0;color:var(--t3);font-size:.75rem;border-top:1px solid var(--br);margin-top:16px\">Student Performance Engine - College Project</div>';\n"
+"  document.getElementById('sr-c').innerHTML=h;\n"
+"}\n"

// teacher overview
+"async function renderTO(){\n"
+"  await loadAS();\n"
+"  var avgs=as.map(function(s){var vs=subs.map(function(sb){return parseFloat(s.grades['g_'+sb])||0;}).filter(function(v){return v>0;});return vs.length?vs.reduce(function(a,b){return a+b;},0)/vs.length:0;});\n"
+"  var vld=avgs.filter(function(v){return v>0;}),ca=vld.length?(vld.reduce(function(a,b){return a+b;},0)/vld.length).toFixed(1):0;\n"
+"  document.getElementById('to-s').innerHTML='<div class=\"sc c1\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#128101;</div><div class=\"sv\">'+as.length+'</div><div class=\"sl\">Students</div></div>'\n"
+"    +'<div class=\"sc c3\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#128202;</div><div class=\"sv\">'+ca+'%</div><div class=\"sl\">Class Avg</div></div>'\n"
+"    +'<div class=\"sc c2\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#127775;</div><div class=\"sv\">'+avgs.filter(function(v){return v>=80;}).length+'</div><div class=\"sl\">Excelling</div></div>'\n"
+"    +'<div class=\"sc c4\"><div style=\"font-size:1.4rem;margin-bottom:8px\">&#9888;</div><div class=\"sv\">'+avgs.filter(function(v){return v>0&&v<60;}).length+'</div><div class=\"sl\">At-Risk</div></div>';\n"
+"  var sa=subs.map(function(sb){var vs=as.map(function(st){return parseFloat(st.grades['g_'+sb])||0;}).filter(function(v){return v>0;});return vs.length?(vs.reduce(function(a,b){return a+b;},0)/vs.length).toFixed(1):0;});\n"
+"  if(tSAC)tSAC.destroy();\n"
+"  tSAC=new Chart(document.getElementById('tSA'),{type:'bar',data:{labels:subs,datasets:[{label:'Avg',data:sa,backgroundColor:sa.map(function(v){return v>=80?'rgba(46,213,115,.7)':v>=60?'rgba(255,179,71,.7)':'rgba(255,71,87,.7)';}),borderRadius:7,borderSkipped:false}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{y:{min:0,max:100}}}});\n"
+"  var dist=[avgs.filter(function(v){return v>=80;}).length,avgs.filter(function(v){return v>=60&&v<80;}).length,avgs.filter(function(v){return v>=40&&v<60;}).length,avgs.filter(function(v){return v>0&&v<40;}).length];\n"
+"  if(tDC)tDC.destroy();\n"
+"  tDC=new Chart(document.getElementById('tD'),{type:'doughnut',data:{labels:['Excellent','Good','Average','Needs Help'],datasets:[{data:dist,backgroundColor:['rgba(46,213,115,.8)','rgba(108,99,255,.8)','rgba(255,179,71,.8)','rgba(255,71,87,.8)'],borderWidth:0}]},options:{responsive:true,maintainAspectRatio:false,cutout:'62%'}});\n"
+"  var rkd=as.map(function(s,i){return{name:s.name,email:s.email,avg:avgs[i]};}).filter(function(s){return s.avg>0;}).sort(function(a,b){return b.avg-a.avg;});\n"
+"  var ph='';if(rkd.length>=3){ph='<div class=\"pi\"><div class=\"pb r2\">&#129352;</div><div class=\"pn\">'+rkd[1].name+'</div><div class=\"ps\">'+rkd[1].avg.toFixed(1)+'%</div></div><div class=\"pi\"><div class=\"pb r1\">&#129351;</div><div class=\"pn\">'+rkd[0].name+'</div><div class=\"ps\">'+rkd[0].avg.toFixed(1)+'%</div></div><div class=\"pi\"><div class=\"pb r3\">&#129353;</div><div class=\"pn\">'+rkd[2].name+'</div><div class=\"ps\">'+rkd[2].avg.toFixed(1)+'%</div></div>';}\n"
+"  else ph=rkd.map(function(s,i){return'<div class=\"pi\"><div class=\"pb r'+(i+1)+'\">'+['&#129351;','&#129352;','&#129353;'][i]+'</div><div class=\"pn\">'+s.name+'</div><div class=\"ps\">'+s.avg.toFixed(1)+'%</div></div>';}).join('')||'<p style=\"color:var(--t3)\">No data yet</p>';\n"
+"  document.getElementById('tpod').innerHTML=ph;\n"
+"  var rk=rkd.filter(function(s){return s.avg<60;});\n"
+"  document.getElementById('tar').innerHTML=rk.length?rk.map(function(s){return'<div class=\"ri\"><div class=\"rd2\"></div><div><strong>'+s.name+'</strong> &mdash; '+s.avg.toFixed(1)+'%<div style=\"font-size:.75rem;color:var(--t2)\">'+s.email+'</div></div><button class=\"btn bsm bk\" style=\"margin-left:auto\" onclick=\"goEdit(\\''+s.email+'\\')\" >Edit</button></div>';}).join(''):'<p style=\"color:var(--g);font-weight:700\">&#10003; No at-risk students!</p>';\n"
+"  document.getElementById('tsc').textContent=as.length+' students';\n"
+"  document.getElementById('ttb').innerHTML=as.map(function(s,i){var a=avgs[i],st=a>=80?'Excellent':a>=60?'Good':a>=40?'Average':a>0?'Needs Help':'No Data',tc=a>=80?'gr':a>=60?'bl':a>=40?'yw':'rd';return'<tr><td><strong>'+s.name+'</strong></td><td>'+s.email+'</td><td style=\"font-weight:800\">'+(a?a.toFixed(1)+'%':'&mdash;')+'</td><td><span class=\"tg '+tc+'\">'+st+'</span></td><td><button class=\"btn bsm bp\" onclick=\"goEdit(\\''+s.email+'\\')\" >&#9999; Edit</button></td></tr>';}).join('');\n"
+"}\n"

+"function goEdit(em){showPg('te');setTimeout(function(){document.getElementById('tes').value=em;loadEP();},200);}\n"

// edit grades
+"async function fillDD(){\n"
+"  await loadAS();\n"
+"  var opt='<option value=\"\">--- Select student ---</option>'+as.map(function(s){return'<option value=\"'+s.email+'\">'+s.name+' ('+s.email+')</option>';}).join('');\n"
+"  ['tes','als'].forEach(function(id){var e=document.getElementById(id);if(e)e.innerHTML=opt;});\n"
+"}\n"

+"async function loadEP(){\n"
+"  var em=document.getElementById('tes').value;\n"
+"  if(!em){document.getElementById('tep').style.display='none';return;}\n"
+"  var st=null;for(var i=0;i<as.length;i++)if(as[i].email===em){st=as[i];break;}\n"
+"  if(!st)return;\n"
+"  document.getElementById('tep').style.display='block';\n"
+"  document.getElementById('ten').textContent=st.name;\n"
+"  var gh='';\n"
+"  for(var i=0;i<subs.length;i++){var s=subs[i],k=s.replace(/ /g,'-'),val=st.grades['g_'+s]||'',col=gc(parseFloat(val));gh+='<div class=\"subc\"><div class=\"subn\">'+s+'</div><input type=\"number\" min=\"0\" max=\"100\" placeholder=\"0-100\" value=\"'+val+'\" id=\"te-'+k+'\" oninput=\"updEC()\"/><div class=\"gl\" style=\"background:'+col+'22;color:'+col+'\">'+( val?gl(parseFloat(val)):'&mdash;')+'</div></div>';}\n"
+"  document.getElementById('teg').innerHTML=gh;\n"
+"  updEC();\n"
+"  var rems=await getRem(em);\n"
+"  document.getElementById('ter').innerHTML=rems.length?rems.slice().reverse().map(remCard).join(''):'<p style=\"color:var(--t3)\">No remarks yet.</p>';\n"
+"}\n"

+"function updEC(){\n"
+"  var vs=subs.map(function(s){var e=document.getElementById('te-'+s.replace(/ /g,'-'));return e?parseFloat(e.value)||0:0;});\n"
+"  var cs=vs.map(function(v){return v>=80?'rgba(46,213,115,.8)':v>=60?'rgba(255,179,71,.8)':'rgba(255,71,87,.8)';});\n"
+"  if(tBC)tBC.destroy();\n"
+"  tBC=new Chart(document.getElementById('teB'),{type:'bar',data:{labels:subs,datasets:[{label:'Score',data:vs,backgroundColor:cs,borderRadius:7,borderSkipped:false}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{y:{min:0,max:100}}}});\n"
+"  if(tRC)tRC.destroy();\n"
+"  tRC=new Chart(document.getElementById('teR'),{type:'radar',data:{labels:subs,datasets:[{label:'Score',data:vs,backgroundColor:'rgba(255,101,132,.2)',borderColor:'#FF6584',pointBackgroundColor:'#FF6584',borderWidth:2}]},options:{responsive:true,maintainAspectRatio:false,scales:{r:{min:0,max:100}}}});\n"
+"}\n"

+"async function saveTG(){\n"
+"  var em=document.getElementById('tes').value;if(!em)return;\n"
+"  var data={email:em};\n"
+"  for(var i=0;i<subs.length;i++){var e=document.getElementById('te-'+subs[i].replace(/ /g,'-'));if(e)data['g_'+subs[i]]=e.value||'';}\n"
+"  try{\n"
+"    var r=await fetch('/api/grades',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});\n"
+"    var d=await r.json();\n"
+"    var nm='';for(var i=0;i<as.length;i++)if(as[i].email===em){nm=as[i].name;break;}\n"
+"    if(d.ok){toast('Saved for '+nm,'s');await loadAS();}else toast('Error','e');\n"
+"  }catch(e){toast('Server error','e');}\n"
+"}\n"

+"async function prSR(){\n"
+"  var em=document.getElementById('tes').value;if(!em){toast('Select student first','e');return;}\n"
+"  var st=null;for(var i=0;i<as.length;i++)if(as[i].email===em){st=as[i];break;}\n"
+"  var rems=await getRem(em);\n"
+"  var sc=subs.map(function(s){return parseFloat(st.grades['g_'+s])||0;}).filter(function(v){return v>0;});\n"
+"  var avg=sc.length?(sc.reduce(function(a,b){return a+b;},0)/sc.length).toFixed(1):0;\n"
+"  var w=window.open('','_blank');\n"
+"  w.document.write('<html><head><title>Report</title><style>body{font-family:Arial;padding:26px;max-width:660px;margin:auto}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:8px;text-align:left}th{background:#6C63FF;color:#fff}.hd{text-align:center;margin-bottom:24px}</style></head><body><div class=\"hd\"><h2>Student Performance Report Card</h2><p>'+st.name+' | '+st.email+'</p><p>Avg: <b>'+avg+'% ('+gl(avg)+')</b></p></div><table><tr><th>Subject</th><th>Score</th><th>Grade</th></tr>'+subs.map(function(s){var v=parseFloat(st.grades['g_'+s])||0;return'<tr><td>'+s+'</td><td>'+(v||'&mdash;')+'</td><td>'+(v?gl(v):'&mdash;')+'</td></tr>';}).join('')+'</table>'+(rems.length?'<h3 style=\"margin-top:16px\">Remarks</h3>'+rems.map(function(r){return'<div style=\"border-left:4px solid #6C63FF;padding:8px;margin:6px 0\"><b>'+r.type+'</b> &mdash; '+r.timestamp+'<br>'+r.message+'</div>';}).join(''):'')+'<scr'+'ipt>window.print();</scr'+'ipt></body></html>');\n"
+"}\n"

// alerts
+"function pickT(el){\n"
+"  document.querySelectorAll('.atb').forEach(function(b){b.classList.remove('on');});\n"
+"  el.classList.add('on'); cat=el.getAttribute('data-t');\n"
+"}\n"

+"function autoR(){\n"
+"  var em=document.getElementById('als').value;\n"
+"  var st=null;for(var i=0;i<as.length;i++)if(as[i].email===em){st=as[i];break;}\n"
+"  var nm=st?st.name:'the student';\n"
+"  var sc=st?subs.map(function(s){return parseFloat(st.grades['g_'+s])||0;}).filter(function(v){return v>0;}).reduce(function(a,b){return a+b;},0)/(subs.map(function(s){return parseFloat(st.grades['g_'+s])||0;}).filter(function(v){return v>0;}).length||1):0;\n"
+"  var avg=sc.toFixed(1);\n"
+"  var msgs={'Praise':'Dear '+nm+', your dedication has led to an impressive average of '+avg+'%. Keep up the excellent work!','Performance Warning':'Dear '+nm+', your current average of '+avg+'% shows you may need help. Please meet me so we can make an improvement plan.','Attendance Alert':'Dear '+nm+', your attendance is below required levels. Please attend all classes regularly.','Custom':'Dear '+nm+', I wanted to reach out about your progress this term. Let us work together to help you achieve your goals.'};\n"
+"  document.getElementById('alm').value=msgs[cat]||msgs['Custom'];\n"
+"}\n"

+"async function sendR(){\n"
+"  var em=document.getElementById('als').value,msg=document.getElementById('alm').value.trim();\n"
+"  var st2=document.getElementById('als2');\n"
+"  if(!em){st2.style.color='var(--r)';st2.textContent='Select a student.';return;}\n"
+"  if(!msg){st2.style.color='var(--r)';st2.textContent='Write a message.';return;}\n"
+"  var st=null;for(var i=0;i<as.length;i++)if(as[i].email===em){st=as[i];break;}\n"
+"  try{\n"
+"    var r=await fetch('/api/remarks',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({studentEmail:em,studentName:st?st.name:em,teacherName:cu.name,type:cat,message:msg})});\n"
+"    var d=await r.json();\n"
+"    if(d.ok){\n"
+"      st2.style.color='var(--g)';st2.textContent='Sent!';\n"
+"      document.getElementById('alm').value='';\n"
+"      rsc++;document.getElementById('rsc').textContent=rsc+' sent';\n"
+"      var dv=document.createElement('div');\n"
+"      dv.className='rem '+(cat==='Praise'?'pr':cat.includes('Warning')?'wa':'at');\n"
+"      dv.innerHTML='<div class=\"rh\"><span class=\"rt\">'+cat+' &mdash; '+(st?st.name:em)+'</span><span class=\"rd3\">'+new Date().toLocaleTimeString()+'</span></div><div class=\"rb\">'+msg+'</div>';\n"
+"      document.getElementById('slog').prepend(dv);\n"
+"      setTimeout(function(){st2.textContent='';},3000);\n"
+"    }\n"
+"  }catch(e){st2.style.color='var(--r)';st2.textContent='Error.';}\n"
+"}\n"

+"async function loadAR(){\n"
+"  var em=document.getElementById('als').value;if(!em)return;\n"
+"  var rems=await getRem(em);\n"
+"  document.getElementById('slog').innerHTML=rems.slice().reverse().map(remCard).join('')||'<p style=\"color:var(--t3)\">No remarks yet.</p>';\n"
+"}\n"

// class report
+"async function genCR(){\n"
+"  await loadAS();\n"
+"  var avgs=as.map(function(s){var vs=subs.map(function(sb){return parseFloat(s.grades['g_'+sb])||0;}).filter(function(v){return v>0;});return vs.length?vs.reduce(function(a,b){return a+b;},0)/vs.length:0;});\n"
+"  var vld=avgs.filter(function(v){return v>0;}),ca=vld.length?(vld.reduce(function(a,b){return a+b;},0)/vld.length).toFixed(1):0;\n"
+"  var rkd=as.map(function(s,i){return{name:s.name,email:s.email,avg:avgs[i]};}).filter(function(s){return s.avg>0;}).sort(function(a,b){return b.avg-a.avg;});\n"
+"  var h='<div class=\"cd\"><h2 style=\"text-align:center;font-family:Poppins,sans-serif;color:var(--p);margin-bottom:4px\">Class Performance Report</h2><p style=\"text-align:center;color:var(--t2);margin-bottom:20px\">'+new Date().toLocaleDateString('en-IN',{day:'2-digit',month:'long',year:'numeric'})+'</p>';\n"
+"  h+='<div style=\"display:grid;grid-template-columns:repeat(4,1fr);gap:13px;margin-bottom:24px\">';\n"
+"  h+='<div style=\"text-align:center;background:var(--bg);border-radius:12px;padding:13px\"><div style=\"font-size:1.6rem;font-weight:800;color:var(--p)\">'+as.length+'</div><div style=\"font-size:.77rem;color:var(--t2)\">Students</div></div>';\n"
+"  h+='<div style=\"text-align:center;background:var(--bg);border-radius:12px;padding:13px\"><div style=\"font-size:1.6rem;font-weight:800;color:var(--t)\">'+ca+'%</div><div style=\"font-size:.77rem;color:var(--t2)\">Class Avg</div></div>';\n"
+"  h+='<div style=\"text-align:center;background:var(--bg);border-radius:12px;padding:13px\"><div style=\"font-size:1.6rem;font-weight:800;color:var(--g)\">'+avgs.filter(function(v){return v>=80;}).length+'</div><div style=\"font-size:.77rem;color:var(--t2)\">Excellent</div></div>';\n"
+"  h+='<div style=\"text-align:center;background:var(--bg);border-radius:12px;padding:13px\"><div style=\"font-size:1.6rem;font-weight:800;color:var(--r)\">'+avgs.filter(function(v){return v>0&&v<60;}).length+'</div><div style=\"font-size:.77rem;color:var(--t2)\">At-Risk</div></div></div>';\n"
+"  h+='<h3 style=\"margin-bottom:11px\">Subject Averages</h3><table class=\"tb\"><thead><tr><th>Subject</th><th>Avg</th><th>Highest</th><th>Lowest</th><th>Status</th></tr></thead><tbody>';\n"
+"  for(var j=0;j<subs.length;j++){var sb=subs[j],vs=as.map(function(st){return parseFloat(st.grades['g_'+sb])||0;}).filter(function(v){return v>0;});var sa=vs.length?(vs.reduce(function(a,b){return a+b;},0)/vs.length).toFixed(1):0,hi=vs.length?Math.max.apply(null,vs):0,lo=vs.length?Math.min.apply(null,vs):0;h+='<tr><td>'+sb+'</td><td style=\"font-weight:700\">'+(sa?sa+'%':'&mdash;')+'</td><td style=\"color:var(--g)\">'+(hi?hi+'%':'&mdash;')+'</td><td style=\"color:var(--r)\">'+(lo?lo+'%':'&mdash;')+'</td><td><span class=\"tg '+(sa>=80?'gr':sa>=60?'yw':'rd')+'\">'+(sa>=80?'Excellent':sa>=60?'Good':sa>0?'Needs Work':'No Data')+'</span></td></tr>';}\n"
+"  h+='</tbody></table>';\n"
+"  if(rkd.length>=3){h+='<h3 style=\"margin:20px 0 11px\">Top Performers</h3><div style=\"display:flex;gap:13px;margin-bottom:20px\">';['&#129351;','&#129352;','&#129353;'].forEach(function(m,i){if(rkd[i])h+='<div style=\"flex:1;text-align:center;background:var(--bg);border-radius:12px;padding:13px\"><div style=\"font-size:1.5rem\">'+m+'</div><strong>'+rkd[i].name+'</strong><div style=\"color:var(--p)\">'+rkd[i].avg.toFixed(1)+'%</div></div>';});h+='</div>';}\n"
+"  var rk=rkd.filter(function(s){return s.avg<60;});\n"
+"  if(rk.length){h+='<h3 style=\"margin:0 0 11px\">Needs Attention</h3>';rk.forEach(function(s){h+='<div class=\"ri\"><div class=\"rd2\"></div><div><strong>'+s.name+'</strong> &mdash; '+s.avg.toFixed(1)+'% &mdash; '+s.email+'</div></div>';});}\n"
+"  h+='<h3 style=\"margin:20px 0 11px\">Full Roster</h3><table class=\"tb\"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Avg</th><th>Grade</th><th>Status</th></tr></thead><tbody>';\n"
+"  as.forEach(function(s,i){var a=avgs[i],tc=a>=80?'gr':a>=60?'bl':a>=40?'yw':'rd',tx=a>=80?'Excellent':a>=60?'Good':a>=40?'Average':a>0?'Needs Help':'No Data';h+='<tr><td>'+(i+1)+'</td><td>'+s.name+'</td><td>'+s.email+'</td><td style=\"font-weight:700\">'+(a?a.toFixed(1)+'%':'&mdash;')+'</td><td>'+(a?gl(a):'&mdash;')+'</td><td><span class=\"tg '+tc+'\">'+tx+'</span></td></tr>';});\n"
+"  h+='</tbody></table></div>';\n"
+"  document.getElementById('tr-c').innerHTML=h;\n"
+"  toast('Report generated!','s');\n"
+"}\n"

+"function prCR(){\n"
+"  var ct=document.getElementById('tr-c').innerHTML;\n"
+"  if(!ct.trim()){toast('Generate first!','e');return;}\n"
+"  var w=window.open('','_blank');\n"
+"  w.document.write('<html><head><title>Class Report</title><style>body{font-family:Arial;padding:20px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:8px;text-align:left}th{background:#6C63FF;color:#fff}.tg,.gr,.yw,.rd,.bl{padding:3px 8px;border-radius:10px;font-size:.77rem;font-weight:700}.gr{background:#D4F5E4;color:#1A7A4A}.yw{background:#FFF3D4;color:#7A5A00}.rd{background:#FFE4E7;color:#8B0000}.bl{background:#E0E7FF;color:#4B44CC}.ri{display:flex;align-items:center;gap:10px;padding:8px;background:#FFE4E7;border-radius:8px;margin:5px 0}.rd2{width:8px;height:8px;background:#FF4757;border-radius:50%}</style></head><body>'+ct+'<scr'+'ipt>window.print();</scr'+'ipt></body></html>');\n"
+"}\n"

+"document.addEventListener('keydown',function(e){\n"
+"  if(e.key!=='Enter')return;\n"
+"  if(document.getElementById('auth').style.display==='none')return;\n"
+"  if(document.getElementById('liF').style.display!=='none')doLogin();else doReg();\n"
+"});\n";
    }
}