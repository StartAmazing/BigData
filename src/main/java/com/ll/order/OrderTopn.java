package com.ll.order;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class OrderTopn {

    public static class OrderTopnMapper extends Mapper<LongWritable, Text, Text, OrderBean>{

        OrderBean orderBean = new OrderBean();
        Text k = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] split = value.toString().split(",");

            orderBean.set(split[0], split[1], split[2],Float.parseFloat(split[3]),Integer.parseInt(split[4]));
            k.set(split[0]);

            //从这里交给mapTask的kv对象，会被maptask序列化后再存储，所以不用担心覆盖的问题
            context.write(k,orderBean);

        }
    }

    public static class OrderTopnReducer extends Reducer<Text, OrderBean,OrderBean, NullWritable>{

        @Override
        protected void reduce(Text key, Iterable<OrderBean> values, Context context)
                throws IOException, InterruptedException {

            //获取topn的参数
            int topn = context.getConfiguration().getInt("order.top.n", 3);

            ArrayList<OrderBean> beanList = new ArrayList<OrderBean>();

            //reduce task提供的value的迭代器，每次迭代返回给我们的都是同一个对象，只是set了不同的值
            for (OrderBean orderBean : values) {

                //构造一个新的对象，用来存储本次迭代出来的值
                OrderBean newBean = new OrderBean();
                newBean.set(orderBean.getOrderId(),orderBean.getUserId(),orderBean.getPdtName(),orderBean.getPrice(),orderBean.getNumber());

                beanList.add(newBean);
            }

            //对beanList中的orderBean对象排序（按总金额大小倒序排序，如果总金额相同，则以商品名称排序）
            Collections.sort(beanList);
            for (int i = 0; i < topn; i++) {
                context.write(beanList.get(i), NullWritable.get());
            }
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
        Configuration conf = new Configuration();

        conf.set("fs.defaultFs", "hdfs://master:9000/");
        conf.set("mapreduce.framework.name","yarn");
        conf.set("mapreduce.resourcename.hostname","master");
        conf.setInt("order.top.n",2);

        Job job = Job.getInstance(conf);

        job.setJarByClass(OrderTopn.class);

        job.setMapperClass(OrderTopnMapper.class);
        job.setReducerClass(OrderTopnReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(OrderBean.class);

        job.setOutputKeyClass(OrderBean.class);
        job.setOutputValueClass(NullWritable.class);

        FileInputFormat.setInputPaths(job, new Path("/order/input/"));
        FileOutputFormat.setOutputPath(job, new Path("/order/output/"));

        job.setNumReduceTasks(2);

        boolean b = job.waitForCompletion(true);

        System.exit(b ? 0 : -1);

    }

}
