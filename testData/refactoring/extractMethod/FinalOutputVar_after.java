class C {
    {
        final int i = newMethod();
        System.out.println("i = " + i);

        final int j = newMethod();
    }

    private int newMethod() {
        final int i = 128;
        return i;
    }
}