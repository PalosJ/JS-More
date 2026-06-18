package com.palos.jsrevise.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class FloatingModelGeometryResolverTest {
    @Test
    void usesTheHighestCubeSurfaceInsteadOfTheLowestOrigin() {
        String model = """
                {
                  "minecraft:geometry": [{
                    "bones": [
                      {"name": "root"},
                      {"name": "body", "cubes": [
                        {"origin": [-5.5, 15.5, 7], "size": [11, 14, 12]},
                        {"origin": [-1, -0.6, 0], "size": [2, 1, 2], "inflate": 0.1}
                      ]}
                    ]
                  }]
                }
                """;

        FloatingModelGeometryResolver.ModelGeometry geometry = FloatingModelGeometryResolver.parseGeometry(
                JsonParser.parseString(model).getAsJsonObject()
        );

        assertEquals(-0.7D, geometry.minimumY(), 1.0E-9D);
        assertEquals(29.5D, geometry.maximumY(), 1.0E-9D);
        assertEquals(-5.5D, geometry.minimumX(), 1.0E-9D);
        assertEquals(5.5D, geometry.maximumX(), 1.0E-9D);
        assertEquals(-0.1D, geometry.minimumZ(), 1.0E-9D);
        assertEquals(19.0D, geometry.maximumZ(), 1.0E-9D);
    }
}
