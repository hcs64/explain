class ExposeMethodSpec {
    // for regular methods
    public ExposeMethodSpec(Class c, String n, Class [] a) {
        clas = c;
        name = n;
        args = a;

        is_ctor = false;
    }

    // for constructors
    public ExposeMethodSpec(Class c, Class [] a) {
        clas = c;
        name = "";
        args = a;

        is_ctor = true;
    }

    private boolean is_ctor;
    private Class<?> clas;
    private String name;
    private Class[] args;

    public void exposeTo(bsh.BshClassManager manager) throws NoSuchMethodException {
        if (is_ctor) {
            //manager.cacheResolvedMethod(clas, args, clas.getConstructor(args));
        } else {
            manager.cacheResolvedMethod(clas, args, clas.getMethod(name, args));
        }
    }

    public static ExposeMethodSpec[] buildArray(Class clas, String[] names, Class[][] args, int n) {
        ExposeMethodSpec[] arr = new ExposeMethodSpec[n];
        for (int i = 0; i < n; i++) {
            arr[i] = new ExposeMethodSpec(clas, names[i], args[i]);
        }

        return arr;
    }

    public static ExposeMethodSpec[] buildArray(Class clas, String[] names, Class[][] args, int n, Class[][] ctor_args, int ctor_n) {
        ExposeMethodSpec[] arr = new ExposeMethodSpec[n+ctor_n];
        for (int i = 0; i < n; i++) {
            arr[i] = new ExposeMethodSpec(clas, names[i], args[i]);
        }

        for (int i = 0; i < ctor_n; i++) {
            arr[n+i] = new ExposeMethodSpec(clas, ctor_args[i]);
        }

        return arr;
    }
}

