package net.preibisch.mvrecon.headless.definedataset;

import java.util.Properties;

/**
 * Created by moon on 7/2/15.
 */
public class StackListParameters
{
	// StackListImageJ
	// StackListLOCI
	public enum Container { ArrayImg, CellImg }
	public Container container = Container.ArrayImg;

	public String timepoints = "18,19,30";
	public String channels = "1,2";
	public String illuminations = "0,1";
	public String angles = "0-315:45";
	public String tiles = "0,1";
	public String directory = ".";

	public enum AngleOption { OneAngle, OneFilePerAngle, AllAnglesInOneFile }
	public AngleOption multipleAngleOption = AngleOption.OneFilePerAngle;

	public enum TimePointOption { OneTimePoint, OneFilePerTimePoint, AllTimePointsInOneFile }
	public TimePointOption multipleTimePointOption = TimePointOption.OneFilePerTimePoint;

	public enum ChannelOption { OneChannel, OneFilePerChannel, AllChannelsInOneFile }
	public ChannelOption multipleChannelOption = ChannelOption.OneChannel;

	public enum IlluminationOption { OneIllumination, OneFilePerIllumination, AllIlluminationsInOneFile }
	public IlluminationOption multipleIlluminationOption = IlluminationOption.OneIllumination;

	public enum TileOption { OneTile, OneFilePerTile, AllTilesInOneFile }
	public TileOption multipleTileOption = TileOption.OneTile;

	public void parseProperties( final Properties props )
	{
		container = Container.valueOf( props.getProperty( "container", "ArrayImg" ) );

		timepoints = props.getProperty( "timepoints" );

		channels = props.getProperty( "channels" );

		illuminations = props.getProperty( "illuminations" );

		tiles = props.getProperty( "tiles" );

		angles = props.getProperty( "angles" );

		directory = props.getProperty( "directory" );

		multipleAngleOption = AngleOption.valueOf( props.getProperty( "has_multiple_angle", "OneFilePerAngle" ) );

		multipleTimePointOption = TimePointOption.valueOf( props.getProperty( "has_multiple_timepoints", "OneFilePerTimePoint" ) );

		multipleChannelOption = ChannelOption.valueOf( props.getProperty( "has_multiple_channels", "OneChannel" ) );

		multipleIlluminationOption = IlluminationOption.valueOf( props.getProperty( "has_multiple_illuminations", "OneIllumination" ) );

		multipleTileOption = TileOption.valueOf( props.getProperty( "has_multiple_tiles", "OneTile" ) );
	}
}
