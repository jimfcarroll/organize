{
  "type": "record",
  "name": "UniversalType",
  "fields": [
    {
      "name": "value",
      "type": [
        "null",
        "boolean",
        "int",
        "long",
        "float",
        "double",
        "bytes",
        "string",
        {
          "type": "array",
          "items": [
            "null",
            "boolean",
            "int",
            "long",
            "float",
            "double",
            "bytes",
            "string",
            "UniversalType"  // Recursive reference to allow arrays of any type, including UniversalType itself
          ]
        },
        {
          "type": "map",
          "values": [
            "null",
            "boolean",
            "int",
            "long",
            "float",
            "double",
            "bytes",
            "string",
            "UniversalType"  // Recursive reference to allow maps of any type, including UniversalType itself
          ]
        }
      ]
    }
  ]
}
