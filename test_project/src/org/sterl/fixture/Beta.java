package org.sterl.fixture;

public class Beta {
    public String greeting() {
        return new Alpha().hello();
    }
}
