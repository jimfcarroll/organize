public class JavaMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        MapType mapType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class);
        return mapper.readValue(p, mapType);
    }
}

import com.fasterxml.jackson.databind.module.SimpleModule;

SimpleModule module = new SimpleModule();
module.addDeserializer(Map.class, new JavaMapDeserializer());
objectMapper.registerModule(module);
