-- 由于 ODC 4.2.0 userEntity的一些属性的对应的包位置改变了，导致序列化找不到原来的包
truncate table `SPRING_SESSION`;
truncate table `SPRING_SESSION_ATTRIBUTES`;