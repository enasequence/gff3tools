package uk.ac.ebi.embl.gff3tools.validation;

import java.lang.reflect.Method;

public class ValidatorDescriptor {
    private final Object instance;
    private final Method method;

    public ValidatorDescriptor(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
    }

    public Object instance() { return instance; }
    public Method method() { return method; }
}
