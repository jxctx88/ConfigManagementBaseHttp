package cn.memedai.service;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

;

/**
 * Created by chengtx on 2016/6/3.
 */

public class ScanControll {

    //被扫描的包路径
    private  String scanPackage = "";
    //zk客户端
    private CuratorFramework client = null;
    //zk服务器连接地址
    private String connStr = "";
    //应用名
    private String applName = "cmbh";

    private static final String namespace = "webServiceCenter";
    public ScanControll(String scanPackage,String connStr,String applName){
        this.scanPackage = scanPackage;
        this.connStr = connStr;
        this.applName = applName;
        System.out.println("扫描包路径:"+scanPackage + ",zk连接地址:" + connStr + ",应用名称" + applName);

    }


    /**
     * 初始化zk连接
     */
    private void buildZKClient(){
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000,3);
        client = CuratorFrameworkFactory.builder().connectString(connStr)
                .sessionTimeoutMs(10000).retryPolicy(retryPolicy)
                .namespace(namespace).build();

        client.start();
    }

    /**
     * 递归的获取包路径下的所有class类<br/>
     * 如果是jar包也获取
     * 获取指定包下面所有被添加服务注解的类.<br/>
     * 服务注解为controll和方法上的requestMapping
     * @param pack
     *          包路径
     * @return
     */
    public Set<Class<?>> getClasses(String pack){
        //第一个class类的集合
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        //是否循环迭代
        boolean recursive = Boolean.TRUE;
        //获取包的名字,并进行替换
        String packageName = pack;
        //转换为文件路径
        String packageDirName = packageName.replace(".","/");
        //定义一个枚举的集合,并进行循环来处理这个目录下的things
        Enumeration<URL> dirs;

        System.out.println("packageDirName="+ packageDirName);

        try {
            //获取所有包路径,如果有多个jar包就有多个
            dirs = Thread.currentThread().getContextClassLoader()
                    .getResources(packageDirName);
            while(dirs.hasMoreElements()) {
                //获取下一个元素
                URL url = dirs.nextElement();
                System.out.println("path" + url.getPath());
                //得到协议的名称
                String protocol = url.getProtocol();
                //如果是以文件的形式保存在服务器上
                if ("file".equals(protocol)) {
                    System.out.println("file类型的扫描");
                    //获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    //以文件的方式扫描这个包下的文件,并添加到集合中
                    findAndAddClassesInPackageByFile(packageName, filePath,
                            recursive, classes);
                } else if ("jar".equals(protocol)) {
                    //如果是jar包文件
                    //定义一个JarFile
                    System.out.println("jar类型的扫描");
                    JarFile jar;
                    try {
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        //从此jar包得到一个枚举类
                        Enumeration<JarEntry> entries = jar.entries();
                        //同样的进行循环迭代
                        while (entries.hasMoreElements()) {
                            //获取jar里的一个实体,可以是目录和一些jar包里的其他文件,如META-INF等文件
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            //如果是以/开头的
                            if (name.charAt(0) == '/') {
                                //获取后面的字符串
                                name = name.substring(1);
                            }
                            //如果前半部分和定义的包名相同
                            if (name.startsWith(packageDirName)) {
                                int idx = name.lastIndexOf('/');
                                //如果以"/"结尾,是一个包
                                if (idx != -1) {
                                    //获取包名,把"/"替换成"."
                                    packageName = name.substring(0, idx).replace('/', '.');
                                }
                                //如果可以迭代下去,并且是一个包
                                if ((idx != -1) || recursive) {
                                    //如果是一个.class文件,而且不是目录
                                    if (name.endsWith(".class")
                                            && !entry.isDirectory()) {
                                        //去掉后面的".class"获取正真的类名
                                        String className = name.substring(packageName.length() + 1,
                                                name.length() - 6);
                                        try {
                                            //添加到classes
                                            classes.add(Class.forName(packageName + '.' + className));
                                        } catch (ClassNotFoundException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return classes;
    }

    private void findAndAddClassesInPackageByFile(String packageName, String filePath, final boolean recursive, Set<Class<?>> classes) {
        //获取此包的目录,建立一个File
        File dir = new File(filePath);
        //如果不存在或者也不是目录就直接返回
        if(!dir.exists() || !dir.isDirectory()){
            // log.warn("用户定义包名 " + packageName + " 下没有任何文件");
            return;
        }

        //如果存在就获取包下的所有文件,包括目录
        File[] dirFiles = dir.listFiles(new FileFilter() {
            //自定义过滤规则,如果可以循环(包含子目录)或则是以.class结尾的文件(编译好的java类文件)
            public boolean accept(File file) {
                return (recursive && file.isDirectory())
                        ||(file.getName().endsWith(".class"));
            }
        });
        //循环所有文件
        for(File file : dirFiles){
            //如果是目录,则继续扫描
            if(file.isDirectory()){
                findAndAddClassesInPackageByFile(
                        packageName+"."+file.getName(),
                        file.getAbsolutePath(),recursive,classes);
            }else {
                //如果是java类文件,去掉后面的.class只留下类名
                String className = file.getName().substring(0,
                        file.getName().length()-6);
                try{

                    //添加到集合中去
                    //classes.add(Class.forName(packageName+'.'+className));
                    //这里用forName有一些不好,会触发static方法,没有使用ClassLoader的load干净
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(packageName
                    + '.' + className));
                }catch (ClassNotFoundException e){
                    // log.error("添加用户自定义视图类错误 找不到此类的.class文件");
                    e.printStackTrace();
                }
            }
        }

    }


    public void init(){
        try{
            System.out.println("扫描初始化--------");
            //初始化zk客户端
            buildZKClient();
            registBiz( );

            //扫描所有的类
            Set classes = getClasses(scanPackage);
            if(classes.size() < 1)
                return;

            //通过注解得到服务地址
            List<String> services = getServicePath(classes);
            for(String s : services) {
                System.out.println("service=" + s);
            }
            System.out.println("------------------size=");

            //注册各个应用节点
            registBizService(services);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws UnknownHostException {
        InetAddress addr=InetAddress.getLocalHost();
        System.out.println(addr.getHostAddress());
    }

    /**
     * 注册各个应用节点
     * @param services
     */
    private void registBizService(List<String> services) {
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
            //获取IP地址
            String ip = addr.getHostAddress().toString();

            for(String s : services){
                String temp = s.replace("/",".");
                if(temp.startsWith("."))
                    temp = temp.substring(1);
                if(client.checkExists().forPath("/"+ applName+"/"+temp)==null){
                    client.create().creatingParentsIfNeeded()
                            .withMode(CreateMode.PERSISTENT)
                            .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                            .forPath("/"+applName+"/"+temp,("1").getBytes());
                }
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/"+applName+"/"+temp+"/"+ip,ip.getBytes());
            }


        } catch (UnknownHostException e) {
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    /**
     * 获取服务的列表,包括Contrller类上的RequestMapping,和方法的RequestMapping
     * @param classes
     * @return
     */
    private List<String> getServicePath(Set<Class> classes) {
        List<String> services = new ArrayList<String>();
        StringBuffer sb = null;
        Controller classControllerA = null;
        RequestMapping classRequestmA = null;
        RequestMapping methodRequestmA = null;
        Annotation ann = null;
        for(Class cls : classes){
            ann = cls.getAnnotation(Controller.class);
            if(ann == null)
                continue;
            else
                classControllerA = (Controller) ann;

            ann = cls.getAnnotation(RequestMapping.class);
            //获取requestMapping的value值
            String basePath = getRequestMappingPath(ann);
            //获取类的所有方法
            Method ms[] = cls.getMethods();
            if(ms == null || ms.length == 0){
                continue;
            }
            for(Method m : ms){
                ann = m.getAnnotation(RequestMapping.class);
                String path = getRequestMappingPath(ann);
                if(path!=null){
                    sb = new StringBuffer();
                    if(basePath != null)
                        sb.append(basePath);
                     if(path.startsWith("/"))
                         sb.append(path);
                    else
                         sb.append("/"+path);
                }else {
                    continue;
                }
                if(sb != null){
                    services.add(sb.toString());
                }
                sb = null;
            }

        }
        return services;
    }

    /**
     * 获取注解的requestMappingPaht路径
     * @param ann
     * @return
     */
    private String getRequestMappingPath(Annotation ann) {
        if(ann instanceof  RequestMapping) {
            if (ann == null) {
                return null;
            } else {
                RequestMapping rma = (RequestMapping) ann;
                String[] paths = rma.value();
                if (paths != null && paths.length > 0)
                    return paths[0];
                else
                    return null;
            }
        }else {
            return null;
        }

    }

    /**
     * 注册应用根节点
     */
    private void registBiz() {
        try{
            if(client.checkExists().forPath("/"+ applName) == null){
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/"+applName,(applName+"提供的服务列表").getBytes());
            }

        }catch (Exception e){
            e.printStackTrace();
        }

    }


}
