package com.gigaspaces.dih.model;

{{#type-registrar.class-names}}
import com.gigaspaces.dih.model.types.{{.}};
{{/type-registrar.class-names}}
import com.gigaspaces.dih.consumer.CDCInfo;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceTypeManager;

/**
 * This class was auto-generated by GigaSpaces
{{#warnings}}
    * WARNING: {{.}}
{{/warnings}}
 */
public class PipelineTypeRegistrar {
    public static void registerTypes(GigaSpace gigaspace) {
        GigaSpaceTypeManager typeManager = gigaspace.getTypeManager();

        typeManager.registerTypeDescriptor(CDCInfo.getTypeDescriptor());
{{#type-registrar.class-names}}
        typeManager.registerTypeDescriptor({{.}}.getTypeDescriptor());
{{/type-registrar.class-names}}
    }
}