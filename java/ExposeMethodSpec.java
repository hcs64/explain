class ExposeMethodSpec {
    public ExposeMethodSpec(Class c, String n, Class [] a) {
        clas = c;
        name = n;
        args = a;
    }

    private Class<?> clas;
    private String name;
    private Class[] args;

    public void exposeTo(bsh.BshClassManager manager) throws NoSuchMethodException {
        manager.cacheResolvedMethod(clas, args, clas.getMethod(name, args));
    }

    public static ExposeMethodSpec[] buildArray(Class clas, String[] names, Class[][] args, int n) {
        ExposeMethodSpec[] arr = new ExposeMethodSpec[n];
        for (int i = 0; i < n; i++) {
            arr[i] = new ExposeMethodSpec(clas, names[i], args[i]);
        }

        return arr;
    }
}

