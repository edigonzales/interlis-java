package guru.interlis.lsp;

import com.google.gson.*;
import guru.interlis.projection.*;
import java.lang.reflect.Type;

/** Preserves DiagramDocument 1.0's scalar/nullable/exact-number wire shape without a second DTO copy or JSON string envelope. */
final class DiagramWireAdapter implements JsonSerializer<DiagramDocument> {
    @Override public JsonElement serialize(DiagramDocument value,Type type,JsonSerializationContext context) {
        return JsonParser.parseString(new DiagramJsonCodec().encode(value));
    }
}
