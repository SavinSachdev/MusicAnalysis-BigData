package edu.csula.datascience.acquisition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.BSON;
import org.bson.BsonDocument;
import org.bson.Document;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.QueryBuilder;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import edu.csula.datascience.models.Track;
import edu.csula.datascience.models.Tweet;
import twitter4j.Status;

/**
 * An example of Collector implementation using Twitter4j with MongoDB Java
 * driver
 */
public class TwitterCollector implements Collector<Track, Status> {
	MongoClient mongoClient;
	MongoDatabase database;
	MongoCollection<Document> collection;

	public TwitterCollector() {
		// establish database connection to MongoDB
		mongoClient = new MongoClient();

		// select `bd-example` as testing database
		database = mongoClient.getDatabase("bd-example");

		// select collection by name `tweets`
		collection = database.getCollection("tracks");
	}

	@Override
	public Collection<Track> mungee(Collection<Status> src) {
		// go through each src and extract text

		// Lucene Analyzer
		//Analyzer analyzer = new StopAnalyzer(Version.LUCENE_36);

		List<Track> tracks = new ArrayList<Track>();
		for (Status status : src) {

			String dirtyStatus = status.getText();

			dirtyStatus = dirtyStatus.replaceAll("(@)\\S+", "");

			Pattern p = Pattern.compile(Pattern.quote("NowPlaying") + "(.*?)" + Pattern.quote("by"));
			Matcher m = p.matcher(dirtyStatus);
			String trackName = null;

			while (m.find()) {

				trackName = ("\t" + m.group(1));

			}
			if (trackName != null) {
				trackName = trackName.replace("*", "\\*");
				trackName = trackName.replace("?", "\\?");
				trackName = trackName.replace("(", "\\(");
				trackName = trackName.replace(")", "\\)");
				trackName = trackName.replace("+", "\\+");
				trackName = trackName.replace("[", "\\[");
				trackName = trackName.replace("]", "\\]");

			}
			
			if (trackName != null && trackName.contains("#")) {
				trackName = trackName.replaceAll("#[A-Za-z]+", "");

			}
			if (trackName != null && trackName.contains(":")) {
				trackName = trackName.replaceAll(":", "");

				// set the trackName here when we make object
			}
			if (trackName != null && trackName.contains("http"))
				trackName = trackName.replaceAll("(http)\\s+", "");
			if (trackName != null && trackName.contains("-")) {
				trackName = trackName.substring(0, trackName.lastIndexOf("-"));
			}
			// get the dirtyStatus and extract the part after by
			// System.out.println(status.getText());
			if (trackName != null) {

				// get lastindex of by and https

				int indexOfby = dirtyStatus.lastIndexOf("by");
				int indexOfHttps = dirtyStatus.indexOf("https", indexOfby);
				if (indexOfby != -1 && indexOfHttps != -1)

					dirtyStatus = dirtyStatus.substring(indexOfby, indexOfHttps);
				// remove hashtags
				dirtyStatus = dirtyStatus.replaceAll("#[A-Za-z]+", "");

				String[] artistDelimiter = { "?", "from", "on", "in", "-", "|", "#", "@" };
				for (int i = 0; i < artistDelimiter.length; i++) {
					if (dirtyStatus.contains(artistDelimiter[i])) {
						dirtyStatus = dirtyStatus.substring(0, (dirtyStatus.indexOf(artistDelimiter[i])));
						break;
					}
				}

				dirtyStatus = dirtyStatus.replace("by", "");
				String artistName = dirtyStatus;
				
				char[] arr=artistName.toCharArray();
				String finalArtist="";
				for(int i=0;i<arr.length;i++){
					if((int)arr[i]==9835){
						continue;
					}
					finalArtist+=arr[i];
				}
				artistName=finalArtist.trim();
				System.out.println("Artist Name: "+artistName);
				if (trackName != null && artistName != null && trackName.length() > 0 && artistName.length() > 0) {
					//get track by track name and artist name from mongo and if record already exist than 
					//just add the tweet info in the track.
					if (artistName != null) {
						artistName = artistName.replace("*", "\\*");
						artistName = artistName.replace("?", "\\?");
						artistName = artistName.replace("(", "\\(");
						artistName = artistName.replace(")", "\\)");
						artistName = artistName.replace("+", "\\+");
						artistName = artistName.replace("[", "\\[");
						artistName = artistName.replace("]", "\\]");

					}
					System.out.println("Looking for track: "+trackName+" and Artist: "+artistName);
					BasicDBObject doc=new BasicDBObject();
					QueryBuilder query=new QueryBuilder();
					Pattern regex1 = null,regex2 = null;
					
					try{
					regex1 = Pattern.compile(trackName); 
					regex2 = Pattern.compile(artistName); 
					}catch(Exception e){
						break;
					}
					query.and(new QueryBuilder().put("trackName").is(regex1).get(),new QueryBuilder().put("artistName").is(regex2).get());
					doc.putAll(query.get());
					FindIterable iterator=collection.find(doc);
					BasicDBObject tweetobj = new BasicDBObject();
					tweetobj.put("likes", status.getFavoriteCount());
					tweetobj.put("retweets", status.getRetweetCount());
					tweetobj.put("userID", status.getUser().getId());
					tweetobj.put("createdAt", status.getCreatedAt());
					System.out.println("Checking value of iterator"+iterator.first());
					if(iterator.first()==null){
					
					MongoCursor cursor=iterator.iterator();
					
					Track track = new Track();
					BasicDBList tweet_info = new BasicDBList(); 
					Tweet tweet = new Tweet();
					tweet.setTweetId(status.getId());
					// tweet.setCreatedAt(status.getCreatedAt().toString());
					tweet.setLikes(status.getFavoriteCount());
					tweet.setRetweets(status.getRetweetCount());
					tweet.setUser(status.getUser());
					
					tweet_info.add(tweetobj);
					track.setTrackName(trackName);
					List<Tweet> tweetsList=new ArrayList<Tweet>();
					tweetsList.add(tweet);
					track.setArtistName(artistName);
					track.setTweetInfo(tweet_info);
					track.setTrackDate(status.getCreatedAt());
					//track.setTweetInfo(tweetsList);
					//track.setTweetInfo(tweet);
					tracks.add(track);
					}else{
						System.out.println("Entry Already Exist");
						MongoCursor<Document> c = iterator.iterator();
						Document d=c.next();
						ArrayList list=new ArrayList();
						list=(ArrayList) d.get("tweetInfo");
						list.add(tweetobj);
						System.out.println("Array List now:"+list);
						d.replace("tweetInfo", list);
						collection.replaceOne(doc, d);
						//replace in Elastic 
						System.out.println("Duplicate entry in Elastic");
						System.out.println("ID is:"+d.getString("trackId"));
						ElasticSearch es=new ElasticSearch(tracks);
						UpdateRequest ur = new UpdateRequest("tracks","track",d.getString("trackId"));
						
						 XContentBuilder obj=null;
						try {
							obj = XContentFactory.jsonBuilder().startObject().startArray("tweetInfo");
									for (Object object : list) {
										obj.value(object);
									}
									 
						} catch (IOException e) {
							// TODO Auto-generated catch block
							es.client.close();
							es.node.close();
							e.printStackTrace();
							continue;
						}
						try {
							obj.endArray().endObject();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							es.client.close();
							es.node.close();
							continue;
						}
						try {
							ur.doc(obj);
						} catch (Exception e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
							es.client.close();
							es.node.close();
							continue;
						}
						 try {
							es.client.update(ur).get();
							
							es.client.close();
							es.node.close();
							continue;
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ExecutionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							continue;
						}
						
					    
					    
						
						
					
						
					
					}
				}
			}

		}

		return tracks;
	}

@Override
	public void save(Collection<Track> fetchedSongs) {
	
	
			List<Document> documents = fetchedSongs.stream()

					.map(item -> new Document()
							.append("trackId", item.getTrackId())
							.append("trackName", item.getTrackName())
							.append("artistName", item.getArtistName())
							.append("duration", item.getTrackDuration())
							.append("trackPopularity", item.getTrackSpotifyPopularity())
							.append("tweetDate", item.getTrackDate())
							.append("audioProperties", new BasicDBObject().append("loudness", item.getAudioProperties().getLoudness())
									
														.append("liveness", item.getAudioProperties().getLiveness())
														.append("tempo", item.getAudioProperties().getTempo())
														.append("valence", item.getAudioProperties().getValence())
														.append("instrumentalness", item.getAudioProperties().getInstrumentalness())
														.append("danceability", item.getAudioProperties().getDanceability())
														.append("speechiness", item.getAudioProperties().getSpeechiness())
														.append("mode", item.getAudioProperties().getMode())
														.append("acousticness", item.getAudioProperties().getAcousticness())
														.append("energy", item.getAudioProperties().getEnergy()))
									.append("tweetInfo", item.getTweetInfo() ))
					
					.collect(Collectors.toList());

			collection.insertMany(documents);
		

	}

}
