package guru.interlis.lsp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import guru.interlis.projection.DiagramDocument;
import guru.interlis.projection.DiagramJsonCodec;
import java.io.IOException;

/** Preserves the canonical response shape, including required explicit nulls, under LSP4J's default Gson writer. */
final class DiagramWireAdapter extends TypeAdapter<DiagramDocument> {
    private static final TypeAdapter<JsonElement> JSON=new Gson().getAdapter(JsonElement.class);
    @Override public void write(JsonWriter output,DiagramDocument document) throws IOException {
        boolean previous=output.getSerializeNulls();
        try {
            output.setSerializeNulls(true);
            JSON.write(output,JsonParser.parseString(new DiagramJsonCodec().encode(document)));
        } finally {output.setSerializeNulls(previous);}
    }
    @Override public DiagramDocument read(JsonReader input) {
        throw new JsonParseException("DiagramDocument is a server response; use the versioned DiagramRequest input");
    }
}
