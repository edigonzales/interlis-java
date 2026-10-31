package guru.interlis.bridge;

import java.util.Optional;

/** Constant-space envelope scan. Payload strings are skipped, never decoded into a JSON tree. */
record MessageHeader(Optional<String> sessionId,String requestId) {
    static MessageHeader read(String json) {
        if(json==null) throw new BridgeException("INVALID_REQUEST","request JSON is required");
        if(json.length()>SessionLimits.MAX_MESSAGE) throw new BridgeException("LIMIT_EXCEEDED","runtime message UTF-16 limit reached");
        String session=null,request="unknown";int depth=0;
        for(int offset=0;offset<json.length();) {
            char current=json.charAt(offset);
            if(current=='{' || current=='[') { if(++depth>256) throw new BridgeException("INVALID_JSON","JSON nesting limit reached"); offset++; }
            else if(current=='}' || current==']') { depth--;offset++; }
            else if(current=='"') {
                int end=endString(json,offset);int colon=space(json,end);
                if(depth==1 && end-offset<=128 && colon<json.length() && json.charAt(colon)==':') {
                    String key=string(json,offset,end);
                    if("sessionId".equals(key) || "requestId".equals(key)) {
                        int start=space(json,colon+1);
                        if(start<json.length() && json.charAt(start)=='"') {
                            int valueEnd=endString(json,start);
                            if(valueEnd-start>1024) throw new BridgeException("INVALID_REQUEST","envelope identity is too long");
                            String value=string(json,start,valueEnd);
                            if("sessionId".equals(key)) session=value;else request=value;
                        }
                    }
                }
                offset=end;
            } else offset++;
        }
        return new MessageHeader(Optional.ofNullable(session),request);
    }
    private static int endString(String json,int start) {
        int offset=start+1;
        while(offset<json.length()) { char c=json.charAt(offset++);if(c=='"')return offset;if(c=='\\' && offset<json.length())offset++; }
        throw new BridgeException("INVALID_JSON","unterminated JSON string");
    }
    private static int space(String json,int offset) { while(offset<json.length() && Character.isWhitespace(json.charAt(offset)))offset++;return offset; }
    private static String string(String json,int start,int end) {
        try {return ((JsonValue.JsonString)Json.parse(json.substring(start,end))).value();}
        catch(JsonException invalid) {throw new BridgeException("INVALID_JSON","invalid envelope string");}
    }
}
