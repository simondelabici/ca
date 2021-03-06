/**
 * 
 */
package org.epics.ca.test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.ConnectionState;
import org.epics.ca.Constants;
import org.epics.ca.Context;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Alarm;
import org.epics.ca.data.AlarmSeverity;
import org.epics.ca.data.AlarmStatus;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.GraphicEnum;
import org.epics.ca.data.GraphicEnumArray;
import org.epics.ca.data.Metadata;
import org.epics.ca.data.Timestamped;

/**
 * @author msekoranja
 *
 */
public class ChannelTest extends TestCase {

	static final double DELTA = 1e-10; 

	private Context context;
	private CAJTestServer server;
	private static final int TIMEOUT_SEC = 5;
	
	@Override
	protected void setUp() throws Exception {
		server = new CAJTestServer();
		server.runInSeparateThread();
		context = new Context();
	}

	@Override
	protected void tearDown() throws Exception {
		context.close();
		server.destroy();
	}

	public void testConnect() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("no_such_channel_test", Double.class))
		{
			assertNotNull(channel);
			assertEquals("no_such_channel_test", channel.getName());
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			try {
				channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				fail("connected on non-existent channel, timeout expected");
			} catch (TimeoutException tc) {
				// OK
			}
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
		};

		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			assertEquals("adc01", channel.getName());
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
			assertEquals("adc01", channel.getName());
		};
		
		// connect to the previously closed channel
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
		};
		
	}

	
	public void testConnectionListener() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			
			final AtomicInteger connectedCount = new AtomicInteger();
			final AtomicInteger disconnectedCount = new AtomicInteger();
			final AtomicInteger unregsiteredEventCount = new AtomicInteger();
			
			Listener cl = channel.addConnectionListener((c, connected) -> {
				if (c == channel)
				{
					if (connected.booleanValue())
						connectedCount.incrementAndGet();
					else
						disconnectedCount.incrementAndGet();
				}
			});
			assertNotNull(cl);
			
			Listener cl2 = channel.addConnectionListener((c, connected) -> unregsiteredEventCount.incrementAndGet());
			assertNotNull(cl2);
			assertEquals(0, unregsiteredEventCount.get());
			cl2.close();

			channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
			
			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());

			assertEquals(0, unregsiteredEventCount.get());

			channel.close();

			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			// NOTE: close does not notify disconnect
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());
			
			assertEquals(0, unregsiteredEventCount.get());
		};
	}
	
	public void testAccessRightsListener() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			final AtomicInteger aclCount = new AtomicInteger();
			final AtomicInteger unregsiteredEventCount = new AtomicInteger();

			Listener rl = channel.addAccessRightListener((c, ar) -> {
				if (c == channel)
				{
					if (ar == AccessRights.READ_WRITE)
						aclCount.incrementAndGet();
				}
			});
			assertNotNull(rl);
			
			Listener cl2 = channel.addAccessRightListener((c, ar) -> unregsiteredEventCount.incrementAndGet());
			assertNotNull(cl2);
			assertEquals(0, unregsiteredEventCount.get());
			cl2.close();

			channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(AccessRights.READ_WRITE, channel.getAccessRights());
			
			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, aclCount.get());

			assertEquals(0, unregsiteredEventCount.get());

			channel.close();

			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, aclCount.get());
			
			assertEquals(0, unregsiteredEventCount.get());
		};
	}

	public void testProperties() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			
			Map<String, Object> props = channel.getProperties();
			Object nativeTypeCode = props.get(Constants.ChannelProperties.nativeTypeCode.name());
			assertNotNull(nativeTypeCode);
			assertEquals(Short.valueOf((short)6), (Short)nativeTypeCode);
			
			Object nativeElementCount = props.get(Constants.ChannelProperties.nativeElementCount.name());
			assertNotNull(nativeElementCount);
			assertEquals(Integer.valueOf(2), (Integer)nativeElementCount);

			Object nativeType = props.get(Constants.ChannelProperties.nativeType.name());
			assertNotNull(nativeType);
			assertEquals(Double.class, (Class<?>)nativeType);
		};
	}
	
	public static <T> boolean arrayEquals(T arr1, T arr2) throws Exception {
	    Class<?> c = arr1.getClass();
	    if (!c.getComponentType().isPrimitive()) 
	    	c = Object[].class;
	    
	    return (Boolean) Arrays.class.getMethod("equals", c, c).invoke(null, arr1, arr2);
	}
	
	private <T> void internalTestPutAndGet(String channelName, Class<T> clazz, T expectedValue, boolean async) throws Throwable
	{
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect();

			if (async)
			{
				Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertTrue(status.isSuccessful());
			}
			else
				channel.putNoWait(expectedValue);
			
			T value;
			if (async)
			{
				value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
			}
			else
				value = channel.get();
			
			if (clazz.isArray())
				arrayEquals(expectedValue, value);
			else
				assertEquals(expectedValue, value);
		}
	}
	
	private void internalTestValuePutAndGet(boolean async) throws Throwable
	{
		internalTestPutAndGet("adc01", String.class, "12.346", async);	// precision == 3
		internalTestPutAndGet("adc01", Short.class, Short.valueOf((short)123), async);
		internalTestPutAndGet("adc01", Float.class, Float.valueOf(-123.4f), async);
		internalTestPutAndGet("adc01", Byte.class, Byte.valueOf((byte)100), async);
		internalTestPutAndGet("adc01", Integer.class, Integer.valueOf(123456), async);
		internalTestPutAndGet("adc01", Double.class, Double.valueOf(12.3456), async);

		internalTestPutAndGet("adc01", String[].class, new String[] { "12.356", "3.112" }, async);	// precision == 3
		internalTestPutAndGet("adc01", short[].class, new short[] { (short)123, (short)-321 }, async);
		internalTestPutAndGet("adc01", float[].class, new float[] { -123.4f, 321.98f }, async);
		internalTestPutAndGet("adc01", byte[].class, new byte[] { (byte)120, (byte)-120 }, async);
		internalTestPutAndGet("adc01", int[].class, new int[] { 123456, 654321 }, async);
		internalTestPutAndGet("adc01", double[].class, new double[] { 12.82, 3.112 }, async);
	}
	
	public void testValuePutAndGetSync() throws Throwable
	{
		internalTestValuePutAndGet(false);
	}
	
	public void testValuePutAndGetAsync() throws Throwable
	{
		internalTestValuePutAndGet(true);
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private <T, ST, MT extends Metadata<T>> void internalTestMetaPutAndGet(String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Class<? extends Metadata> meta, Alarm<?> expectedAlarm, Control<?, Double> expectedMeta, boolean async) throws Throwable
	{
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect();

			if (async)
			{
				Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertTrue(status.isSuccessful());
			}
			else
				channel.putNoWait(expectedValue);
			
			MT value;
			if (async)
			{
				value = (MT)channel.getAsync(meta).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
			}
			else
				value = channel.get(meta);
			
			if (Alarm.class.isAssignableFrom(meta))
			{
				Alarm<T> v = (Alarm<T>)value;
				assertEquals(expectedAlarm.getAlarmStatus(), v.getAlarmStatus());
				assertEquals(expectedAlarm.getAlarmSeverity(), v.getAlarmSeverity());
			}
			
			if (Timestamped.class.isAssignableFrom(meta))
			{
				Timestamped<T> v = (Timestamped<T>)value;
				long dt = System.currentTimeMillis() - v.getMillis();
				assertTrue(dt < (TIMEOUT_SEC * 1000));
			}

			
			if (Graphic.class.isAssignableFrom(meta))
			{
				Graphic<T, ST> v = (Graphic<T, ST>)value;

				assertEquals(expectedMeta.getUnits(), v.getUnits());
				if (scalarClazz.equals(Double.class) || scalarClazz.equals(Float.class))
					assertEquals(expectedMeta.getPrecision(), v.getPrecision());
				// no NaN or other special values allowed
				assertEquals(expectedMeta.getLowerAlarm(), Number.class.cast(v.getLowerAlarm()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getLowerDisplay(), Number.class.cast(v.getLowerDisplay()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getLowerWarning(), Number.class.cast(v.getLowerWarning()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getUpperAlarm(), Number.class.cast(v.getUpperAlarm()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getUpperDisplay(), Number.class.cast(v.getUpperDisplay()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getUpperWarning(), Number.class.cast(v.getUpperWarning()).doubleValue(), DELTA);
			}

			if (Control.class.isAssignableFrom(meta))
			{
				Control<T, ST> v = (Control<T, ST>)value;
				assertEquals(expectedMeta.getLowerControl(), Number.class.cast(v.getLowerControl()).doubleValue(), DELTA);
				assertEquals(expectedMeta.getUpperControl(), Number.class.cast(v.getUpperControl()).doubleValue(), DELTA);
			}
		
			if (clazz.isArray())
				arrayEquals(expectedValue, value.getValue());
			else
				assertEquals(expectedValue, value.getValue());
		}
	}
	
	private <T, ST> void internalTestMetaPutAndGet(String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Alarm<?> expectedAlarm, Control<?, Double> expectedMeta, boolean async) throws Throwable
	{
		internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Alarm.class, expectedAlarm, expectedMeta, async);	// precision == 3
		internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Timestamped.class, expectedAlarm, expectedMeta, async);
		if (!clazz.equals(String.class) && !clazz.equals(String[].class))
		{
			internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Graphic.class, expectedAlarm, expectedMeta, async);
			internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Control.class, expectedAlarm, expectedMeta, async);
		}
	}
	
	private void internalTestMetaPutAndGet(boolean async) throws Throwable
	{
		Alarm<Double> alarm = new Alarm<Double>();
		alarm.setAlarmStatus(AlarmStatus.UDF_ALARM);
		alarm.setAlarmSeverity(AlarmSeverity.INVALID_ALARM);
		
		Control<Double, Double> meta = new Control<Double, Double>();
		meta.setUpperDisplay(new Double(10));
		meta.setLowerDisplay(new Double(-10));
		meta.setUpperAlarm(new Double(9));
		meta.setLowerAlarm(new Double(-9));
		meta.setUpperControl(new Double(8));
		meta.setLowerControl(new Double(-8));
		meta.setUpperWarning(new Double(7));
		meta.setLowerWarning(new Double(-7));
		meta.setUnits("units");
		meta.setPrecision((short)3);

		
		internalTestMetaPutAndGet("adc01", String.class, String.class, "12.346", alarm, meta, async);	// precision == 3
		internalTestMetaPutAndGet("adc01", Short.class, Short.class, Short.valueOf((short)123), alarm, meta, async);
		internalTestMetaPutAndGet("adc01", Float.class, Float.class, Float.valueOf(-123.4f), alarm, meta, async);
		internalTestMetaPutAndGet("adc01", Byte.class, Byte.class, Byte.valueOf((byte)100), alarm, meta, async);
		internalTestMetaPutAndGet("adc01", Integer.class, Integer.class, Integer.valueOf(123456), alarm, meta, async);
		internalTestMetaPutAndGet("adc01", Double.class, Double.class, Double.valueOf(12.3456), alarm, meta, async);

		internalTestMetaPutAndGet("adc01", String[].class, String.class, new String[] { "12.356", "3.112" }, alarm, meta, async);	// precision == 3
		internalTestMetaPutAndGet("adc01", short[].class, Short.class, new short[] { (short)123, (short)-321 }, alarm, meta, async);
		internalTestMetaPutAndGet("adc01", float[].class, Float.class, new float[] { -123.4f, 321.98f }, alarm, meta, async);
		internalTestMetaPutAndGet("adc01", byte[].class, Byte.class, new byte[] { (byte)120, (byte)-120 }, alarm, meta, async);
		internalTestMetaPutAndGet("adc01", int[].class, Integer.class, new int[] { 123456, 654321 }, alarm, meta, async);
		internalTestMetaPutAndGet("adc01", double[].class, Double.class, new double[] { 12.82, 3.112 }, alarm, meta, async);
	}
	
	public void testMetaPutAndGetSync() throws Throwable
	{
		internalTestMetaPutAndGet(false);
	}
	
	public void testMetaPutAndGetAsync() throws Throwable
	{
		internalTestMetaPutAndGet(true);
	}
	
	private <T> void internalTestGraphicEnum(String channelName, Class<T> clazz, T expectedValue, Alarm<?> expectedAlarm, String[] expectedLabels, boolean async) throws Throwable
	{
		// put
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect();

			if (async)
			{
				Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertTrue(status.isSuccessful());
			}
			else
				channel.putNoWait(expectedValue);
		
			Alarm<T> value;
			@SuppressWarnings("rawtypes")
			Class<? extends Metadata> gec = clazz.isArray() ? GraphicEnumArray.class : GraphicEnum.class;
			if (async)
			{
				value = (Alarm<T>)channel.getAsync(gec).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
			}
			else
			{
				value = (Alarm<T>)channel.get(gec);
			}
	
			if (clazz.isArray())
				arrayEquals(expectedValue, value.getValue());
			else
				assertEquals(expectedValue, value.getValue());

			assertEquals(expectedAlarm.getAlarmStatus(), value.getAlarmStatus());
			assertEquals(expectedAlarm.getAlarmSeverity(), value.getAlarmSeverity());
			
			String[] labels = clazz.isArray() ? ((GraphicEnumArray)value).getLabels() : ((GraphicEnum)value).getLabels();
			assertTrue(Arrays.equals(expectedLabels, labels));
		}
	}
	
	public void testGraphicEnum() throws Throwable
	{
		Alarm<Double> alarm = new Alarm<Double>();
		alarm.setAlarmStatus(AlarmStatus.UDF_ALARM);
		alarm.setAlarmSeverity(AlarmSeverity.INVALID_ALARM);

		final String[] labels =
			{ "zero", "one", "two", "three", "four", "five", "six", "seven" }; 

		internalTestGraphicEnum("enum", Short.class, (short)2, alarm, labels, false);
		internalTestGraphicEnum("enum", Short.class, (short)3, alarm, labels, true);

		internalTestGraphicEnum("enum", short[].class, new short[] { 1, 2 }, alarm, labels, false);
		internalTestGraphicEnum("enum", short[].class, new short[] { 3, 4 }, alarm, labels, true);
	}
	
	public void testMonitors() throws Throwable {
		
		try (Channel<Integer> channel = context.createChannel("counter", Integer.class))
		{
			channel.connect();
			
			try {
				channel.addValueMonitor(null);
				fail("null handler accepted");
			} catch (IllegalArgumentException iae) {
				// ok
			}
			
			try {
				channel.addValueMonitor((value) -> {}, 0, Monitor.VALUE_MASK);
				fail("invalid (0) queue size accepted");
			} catch (IllegalArgumentException iae) {
				// ok
			}
			
			try {
				channel.addValueMonitor((value) -> {}, -1, Monitor.VALUE_MASK);
				fail("invalid negative queue size accepted");
			} catch (IllegalArgumentException iae) {
				// ok
			}

			try {
				channel.addValueMonitor((value) -> {}, 3, Monitor.VALUE_MASK);
				fail("non-pow2 queue size accepted");
			} catch (IllegalArgumentException iae) {
				// ok
			}

			try {
				channel.addValueMonitor((value) -> {}, 2, 0);
				fail("empty mask accepted");
			} catch (IllegalArgumentException iae) {
				// ok
			}

			// note: we accept currently non-valid masks to allow future/unstandard extensions
			
			
			try (Monitor<Integer> m = channel.addValueMonitor((value) -> {}, 2, Monitor.VALUE_MASK)) {
				assertNotNull(m);
			}

			AtomicInteger monitorCount = new AtomicInteger();
			Monitor<Integer> m = channel.addValueMonitor((value) -> monitorCount.incrementAndGet(), 2, Monitor.VALUE_MASK);
			assertNotNull(m);
			Thread.sleep(TIMEOUT_SEC * 1000);
			m.close();
			m.close();
			int monitors = monitorCount.get();
			assertTrue(monitors >= TIMEOUT_SEC); // 1 + TIMEOUT_SEC (where one can be missed)
			Thread.sleep(TIMEOUT_SEC * 1000);
			assertEquals(monitors, monitorCount.get());
			
		}
		
	}
	
	public void testGenericChannel() throws Throwable {
		
		try (Channel<Object> channel = context.createChannel("adc01", Object.class))
		{
			assertNotNull(channel);
			
			channel.connect();

			internalTestValuePutAndGet(false);
			internalTestValuePutAndGet(true);

			internalTestMetaPutAndGet(false);
			internalTestMetaPutAndGet(true);
		}
		
	}
	
	public void testLargeArray() throws Throwable {
		
		tearDown();
		
		final String propName = com.cosylab.epics.caj.cas.CAJServerContext.class.getName() + ".max_array_bytes";
		String oldValue = System.getProperty(propName);
		System.setProperty(propName, String.valueOf(4*1024*1024+1024+32));
		try 
		{
			setUp();
			
			try (Channel<int[]> channel = context.createChannel("large", int[].class))
			{
				channel.connect();

				int[] value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
				
				final int LARGE_PRIME = 15485863;
				for (int i = 0; i < value.length; i++)
				{
					assertEquals(i, value[i]);
					value[i]+= LARGE_PRIME;
				}
				
				Status putStatus = channel.putAsync(value).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertEquals(Status.NORMAL, putStatus);
				
				value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
				
				for (int i = 0; i < value.length; i++)
					assertEquals(i+LARGE_PRIME, value[i]);
			} 
		}
		finally
		{
			// restore value
			if (oldValue == null)
				System.clearProperty(propName);
			else	
				System.setProperty(propName, oldValue);
		}
	}
}
