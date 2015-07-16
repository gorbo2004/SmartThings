/** FLEXi Triggers
	
    
	Version 1.2 (2015-5-5)
 
   The latest version of this file can be found at:
   https://github.com/infofiend/FLEXi_Lighting/FLEXi_Triggers
 
 
   --------------------------------------------------------------------------
 
   Copyright (c) 2015 Anthony Pastor
 
   This program is free software: you can redistribute it and/or modify it
   under the terms of the GNU General Public License as published by the Free
   Software Foundation, either version 3 of the License, or (at your option)
   any later version.
 
   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
   or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
   for more details.
 
   You should have received a copy of the GNU General Public License along
   with this program.  If not, see <http://www.gnu.org/licenses/>.
   
 **/

definition(
    name: "FLEXi Triggers",
    namespace: "info_fiend",
    author: "Anthony Pastor",
   description: "If selected presence / virtual presence switch(es) (for virtual presence use device type FLEXiState) is ON,           " +
    			 "this SmartApp will turn on the selected lights (use device types FLEXiHue, " +
                 "FLEXiDimmer, and any type of basic switch).  This SmartApp will check the        " +
                 "FLEXiHue and FLEXiDimmer devices for scene settings (stored by the FLEXi Lighting   " +
                 "Scenes App) or manually-entered settings (entered in a FLEXi light's individual device tile.   " +
                 "         ---------------------------------------------------------------       " +
                 "Once a triggering event occurs, the correct color and levels (from your Scene or Manual settings) " +
                 "will be used.  In addition, the app asks the user for default settings to use, in the case " +
                 "neither Scene or Manual settings can be found.                                                    " +
                 "Finally, this SmartApp will turn off the lights based on the no-motion time limits (either from " +
                 "the FLEXi Scenes app or from user's default settings in this app.",
    category: "Convenience",
	iconUrl: "https://dl.dropboxusercontent.com/u/2403292/Lightbulb.png",
    iconX2Url: "https://dl.dropboxusercontent.com/u/2403292/Lightbulb%20large.png")

preferences {
	section("All of these people need to be home:"){
    
		input "people", title: "People with Presence Devices:", "capability.presenceSensor", multiple: true, required: false
		input "virtual", title: "Virtual Presence Switches:","device.flexistate", multiple: true, required: false
	}
    
    section("Select motion detector(s) to check for BOTH 'Motion' and 'No Motion' events:"){
		input "motions", "capability.motionSensor", multiple: true, required: false
	}
	section("Select contact sensor(s):"){
		input "contacts", "capability.contactSensor", multiple: true, required: false
	}
    section("Select Hues, Dimmer Lights, and/or Switches to turn on..."){
        input "hues", "device.flexihueBulb", multiple: true, title: "Select Hues", required: false
		input "dimmers", "device.flexidimmer", multiple: true, title: "Select Dimmers", required: false        
        input "switches", "capability.switch", multiple: true, title: "Select Switches", required: false
	}
    
    section("User Abort Options", hideable:true, hidden: true) {
        input "abortToggle", title: "Identify the No-Motion Off Abort Toggle", "capability.momentary", multiple: false, required: false  
		input "abortTime", "number", title: "Check for abort for how many minutes?", multiple: false, required: true, defaultValue: 2  
		input "abortLength", "number", title: "How long should No-Motion events be ignored upon Abort? (minutes):", multiple: false, required: true, defaultValue: 30  
    }
    
    section("Other Options", hideable:true, hidden: true) {

		input "wantPush", "enum", title: "Push mode change?", required: true, metadata: [values: ["yes", "no"]], defaultValue: "no" 
		input "defLevel", "number", title: "Default Hue / Dimmer Level:", required: true, defaultValue: 99
        input "defColor", "enum", title: "Default Hue Color:", required: true, multiple:false, metadata: [values:
					["Warm", "Soft", "Normal", "Daylight", "Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink"]], defaultValue: "Warm"
        input "defSwitch", "enum", title: "Default Switch State:", metadata: [values: ["yes", "no"]], required:true, defaultValue: "no"
		input "defOffTime", "number", title: "Default No Motion OffTime (minutes):", required: true, defaultValue: 30 	        

		input "optNOMotions", title: "For 'NO-Motion' events only:  Also check these Motion Sensors:", "capability.motionSensor", multiple: true, required: false
   		input "optYESMotions", title: "For 'Motion' events only:  Also check these Motion Sensors:", "capability.motionSensor", multiple: true, required: false  
    }
}


def installed()
{
	
	initialize()

}


def updated()
{
	unschedule()
	unsubscribe()
	initialize()
    

}

/** def uninstalled()
{
	unschedule()
	unsubscribe()
  
}
**/

def initialize()
{
	state.abortTime = null
    state.abortWindow = null
	state.inactiveAt = null
	state.allGroup = []   
	state.offTime = defOffTime
    
	subscribe(virtual, "switch", checkHome)
    subscribe(people, "presence", checkHome)
    
    if (hues) {
    	levelCheck() 
    }
    
    if (motions) {
    	subscribe(motions, "motion", motionHandler)
    }
    if (contacts) {
    	subscribe(contacts, "contact", contactHandler)
    }
    if (optNOMotions) {
    	subscribe(optMotions, "motion.inactive", motionHandler)
    }
    if (optYESMotions) {
    	subscribe(optYESMotions, "motion.active", motionHandler)
    }
	if (abortToggle) {
    	subscribe(abortToggle, "momentary.pushed", abortHandler)       
    }
	subscribe(location, onLocation)

	colorCheck()
	schedule("0 * * * * ?", "scheduleCheck")
}

def checkOff() {   	// Wictor Wictor Niner
	def foundOff = null

	foundOff = hues.find{it.currentValue("sceneSwitch") == "Master"} 

    log.debug "foundOff is ${foundOff}."
    
	def offTime = foundOff.currentValue("offTime") ?: defOffTime 
     
    return offTime
     

}

def checkHome() {

	def result = false
    
   	if (allPeopleHome() && allVirtualHome()) {
    
        result = true
        log.debug "allHome is true"
	}
	return result
}      


def allPeopleHome() {

	def result = true
	for (person in people) {
		if (person.currentPresence == "not present") {
			result = false
			break
		}
	}
	log.debug "allPeopleHome: $result"
	return result
}

def allVirtualHome() {

	def result = true
	for (person in virtual) {
		if (person.currentValue("switch") == "off") {
			result = false
			break
		}
	}
	log.debug "allVirtualHome: $result"
	return result
}



def colorCheck() {

	def valueColor = defColor as String
    def newHue = 23
    def newSat = 56
    
	switch(valueColor) {
				
		case "Normal":
			newHue = 52
			newSat = 19
			break;
						
		case "Daylight":
			newHue = 53
			newSat = 91
			break;
                            
		case "Soft":
			newHue = 23
			newSat = 56
			break;
        	                
		case "Warm":
			newHue = 20
			newSat = 80 //83
			break;
    	                    
		case "Blue":
			newHue =  70
			newSat = 100
           	break;
                        
		case "Green":
			newHue = 39
    	    newSat = 100
			break;
                        
		case "Yellow":
        	newHue = 25
			newSat = 100			
    	   	break;
        	                
		case "Orange":
			newHue  = 10
			newSat = 100
			break;
                        
		case "Purple":
			newHue = 75
			newSat = 100
	        break;
                        
		case "Pink":
			newHue = 83
			newSat = 100
		    break;
                        
		case "Red":
			newHue = 100
			newSat = 100                       
			break;
                        
	}

	state.defHue = newHue
    state.defSat = newSat
    
}

def levelCheck() {

   	log.debug "Reached levelCheck.  "
	runEvery5Minutes(isThisIt)

}
 
def isThisIt() {

	hues?.each { 
	    if (it.currentValue("level") == "100") {	      	
    		log.debug "Detected manual switch used - adjusting to current Scene settings."
        	turnON()
        }
    }    
}        
        

def turnON() {							// YEAH, baby!
 
 	def myScene = null
	def masterLight = null    

    masterLight = hues.find{it.currentValue("sceneSwitch") == "Master"}
        
    if (masterLight) {
   	    state.masterLevel = masterLight.currentValue("sceneLevel") as Number ?: defLevel as Number
		state.masterHue = masterLight.currentValue("sceneHue") as Number ?: state.defHue as Number            
		state.masterSat = masterLight.currentValue("sceneSat") as Number ?: state.defSat as Number            
/**	} else {
        state.masterLevel = defLevel as Number
        state.masterHue = state.defHue as Number
        state.masterSat = state.defSat as Number
        
**/
	}
    
	if (hues) {
	
        hues?.each {
            def myHueLevel = null                         
            def scnHue = null
			def scnSat = null
            
            if (it.currentValue("switch") == "off" && it.currentValue("level") > 0) {
            
            	log.debug "${it.label} is off and has a current level value of > 0.  Turning on using appropriate settings."
            
	            myScene = it.currentValue("sceneSwitch")
            	log.debug "${it.label} sceneSwitch is ${myScene}."
                
				if ( myScene == "Master" || myScene == "Slave" ) {

	                myHueLevel = state.masterLevel as Number
                   	log.debug "${it.label} sceneSwitch is ${myScene}."
                    
//                    if (myHueLevel > 0) {
			   			scnHue = state.masterHue as Number
						scnSat = state.masterSat as Number
                    
    		            def newValueColor = [hue: scnHue, saturation: scnSat, level: myHueLevel, transitiontime: 2]
                        log.debug "${it.label} is using Master settings} - newValueColor is ${newValueColor}."
    			        it.setColor(newValueColor)
//					}	
				} else if ( myScene == "Freebie") {
            	    myHueLevel = it.currentValue("sceneLevel")             
//                    if (myHueLevel > 0) {                    
				   		scnHue = it.currentValue("sceneHue")    
						scnSat = it.currentValue("sceneSat")
        	              
						def newValueColor = [hue: scnHue, saturation: scnSat, level: myHueLevel, transitiontime: 2]   		   			
                        log.debug "${it.label} is Free - newValueColor is ${newValueColor}."                        
	    	    	    it.setColor(newValueColor)
//                	}
				} else if ( myScene == "Manual" ) {
					myHueLevel = it.currentValue("level")   		   			
//                    if (myHueLevel > 0) {
	            	    scnHue = it.currentValue("hue")    
						scnSat = it.currentValue("saturation")
					
	    	            def newValueColor = [hue: scnHue, saturation: scnSat, level: myHueLevel, transitiontime: 2]   		   			
                        log.debug "${it.label} is Manual - newValueColor is ${newValueColor}."                        
 		    	        it.setColor(newValueColor)
//					}
	            } else { 
					myHueLevel = defLevel as Number 
//                    if (myHueLevel > 0) {                    
	    	            scnHue = state.defHue as Number    
						scnSat = state.defSat as Number    

        	        	if (myHueLevel > 99) {
            	       		myHueLevel = 99
	            	    }    
		            
    	            	def newValueColor = [hue: scnHue, saturation: scnSat, level: myHueLevel, transitiontime: 2]
                        log.debug "${it.label} is using default settings - ${newValueColor}."                        
	 	    	        it.setColor(newValueColor)                    
//					}
            	}            
            }    
		}
    }
    
    if (dimmers) {
            
       	dimmers?.each {
			def myDimLevel = null        
            
	        if (it.currentValue("switch") == "off" &&  it.currentValue("level") > 0) {
            	log.debug "${it.label} is off and has a current level value of > 0.  Turning on using appropriate settings."
                             
	            myScene = it.currentValue("sceneSwitch")
            	log.debug "${it.label} sceneSwitch is ${myScene}."
                
				if ( myScene == "Master" || myScene == "Slave" ) {
	
                    myDimLevel = state.masterLevel as Number
//                    if (myDimLevel > 0) {
                        log.debug "${it.label} is Master - myDimLevel is ${myDimLevel}."                    
		                it.setLevel(myDimLevel)		                                
//					}				
				} else if ( myScene == "Freebie") {
            					
                    myDimLevel = it.currentValue("sceneLevel") 
//                    if (myDimLevel > 0) {
                        log.debug "${it.label} is Free - myDimLevel is ${myDimLevel}."                    
		                it.setLevel(myDimLevel)		                                  
//					}            
				} else if ( myScene == "Manual" ) {
            
					myDimLevel = it.currentValue("level")   		   			
//                    if (myDimLevel > 0) {             
                        log.debug "${it.label} is Manual - myDimLevel is ${myDimLevel}."                    
		                it.setLevel(myDimLevel)		            					
//					}
	            } else { 
            	
					myDimLevel = defLevel as Number 
//                    if (myDimLevel > 0) {                    
	                    if (myDimLevel > 99) {
    	                	myDimLevel = 99
        	            }    
                        log.debug "${it.label} is using default settings - myDimLevel is ${myDimLevel}."                        
	        	        it.setLevel(myDimLevel)		            
//					}
				}				
            }           
		}
    }

	if (switches) {
    	
  		switches?.each {
    		if (it.currentValue("switch") == "off") {
      			it.on()
	      	}    
		}
        
	}   
}

// Handle motion event.
def motionHandler(evt) {
	def theSensor = motions.find{it.id == evt.deviceId} // evt.deviceId
	
	if (checkHome) {

	    if (evt.value == "active") {
			log.trace "${theSensor.label} detected motion - resetting state.inactiveAt to null & calling turnON()."
			state.inactiveAt = null
            turnON()   
		  	state.abortWindow = null
            
        } else if (evt.value == "inactive") {
			log.trace "${theSensor.label} detected NO motion - setting state.inactiveAt to ${now}."    
       	  	state.inactiveAt = now()
           	setActiveAndSchedule()
        }       			   
        
	} else {
    
		log.debug "Motion event, but checkHome is not true."
    }    
}

// Handle contact event.
def contactHandler(evt) {
	def theSensor = motions.find{it.id == evt.deviceId}
	
//	log.trace "${evt.deviceId} detected ${evt.value}."
	if (checkHome) {
	    if (evt.value == "open") {
			log.trace "${theSensor.label} opened -- resetting state.inactiveAt to null & calling turnON()."
			state.inactiveAt = null
			turnON()   
		  	state.abortWindow = null
            
    	} else {
			log.trace "${theSensor.label} closed -- setting state.inactiveAt to ${now()}."        
			// When contact closes, we reset the timer if not already set
 
 			state.inactiveAt = now()
        	setActiveAndSchedule()
        }  

	} else {
    
		log.debug "Contact event, but checkHome is not true."

	}    
    
}


// Handle location event.
def onLocation(evt) {
    
    pause(500)
    
    if ( wantPush == "yes" ) {
		sendPush("Mode change to ${evt.value}.")
    }
    
	state.currentMode = evt.value
    
    state.lastMode = state.currentMode
	state.inactiveAt = now()
  	state.abortWindow = null   
	
    def newOffTime = checkOff()
    
	log.debug "Mode offTime is ${newOffTime}."
    log.trace "state.inactiveAt = ${now()} & calling setActiveAndSchedule()."    
	setActiveAndSchedule() 

}



def setActiveAndSchedule() {
    unschedule("scheduleCheck")
    def myOffTime = checkOff()
    def mySchTime = myOffTime * 15
    log.debug "setActiveAndSchedule:  myOffTime is ${myOffTime} and mySchTime is ${mySchTime}."
/**    if (mySchTime < 300) {
    	mySchTime = 300
    }    
**/    
    log.debug "setActiveAndSchedule: running scheduleCheck in ${mySchTime} seconds."    
	runIn (mySchTime, "scheduleCheck")  // check monitored lights every 1/4 of offTime limit (in seconds) BUT no more than every 5 minutes	    
}

def scheduleCheck() {
    log.debug "scheduleCheck:  "
    if(state.inactiveAt != null) {

        def minutesOff = checkOff()
        log.debug "Mode offTime is ${minutesOff}."
	    def elapsed = now() - state.inactiveAt
	    def threshold = 60000 * minutesOff
        log.debug "elapsed = ${elapsed} / threshold = ${threshold}."
		

        if (elapsed >= threshold) {                     
            
            
        	if (abortToggle && checkHome) {
            
            							// check for previous abort within abortLength minutes
				if (noPrevAbort) {

       				sendNotificationEvent("${motions.label}:  No Motion - will turn off lights unless Abort received within ${abortTime} minutes.") 
		        	sendPush("ST Trig No Motion from ${motions.label}.")                 
					state.abortWindow = "Valid"        
        	        
                	unschedule("turningOff")
            	    def abortWindow = abortTime as Number
	                abortWindow = abortWindow * 60            
			    
    	           	runIn (abortWindow, "turningOff")                            
                    
                 } else {
                 	log.debug "scheduleCheck:  Previous abort still active."
	      	    	turningOff()                 
                 }
                
   		    } else if (!checkHome) {
            	
	           	log.debug "scheduleCheck:  No one home, so running turningOff() immediately."
      	    	turningOff()
        
           	} else if (!abortToggle) {
                            
                log.debug "scheduleCheck:  no abortToggle within ${abortLength} minutes, so running turningOff() immediately."
	            turningOff()
                    
        	}    
            
	    } else {
    
			setActiveAndSchedule() 
    	}
    }    
}

def noPrevAbort() {

	def result = false
    
    if (state.timeOfAbort) {    
	
    	def elapsed = now() - state.timeOfAbort
		def threshold = 60000 * abortLength
	    log.debug "elapsed = ${elapsed} / abort threshold = ${threshold}."
		
    	if (elapsed >= threshold) {                     
			result = true 
		    log.debug "noPrevAbort is true."
		}
        
    } else {    
		result = true 
	}
    
	return result

}

def abortHandler(evt) {

	log.trace "abortHandler:  abortToggle pushed. "
    log.debug "Receieved evt is ${evt} & evt.value is ${evt.value}."

    if (state.abortWindow == "Valid") {
    
    	unschedule("turningOff")
		state.abortWindow = null
		sendNotificationEvent("Abort command received.  No Motion events cancelled for ${abortLength} minutes.")         
        log.trace "abortHandler:  Received abort command within the abortWindow.  Unscheduling turningOff()."
		state.timeOfAbort = now()
        
    } else {
    
        log.trace "abortHandler:  Received abort command, but not within the abortWindow."
    }    
    
}


def turningOff () {

		state.abortWindow = null
        state.inactiveAt = null        
        
		log.trace "Executing turningOff() . "
 

			if (hues) {
	            hues?.each {
    	          	it.off()
	    	    }
            }
            if (dimmers) {
	            dimmers?.each {
    	        	it.off()
        	    }    
	        }
            if (switches) {
	            switches?.each {
    	        	it.off()
        	    }    
	        }


}


private def TRACE(message) {
    log.debug message
}

private def STATE() {
    log.trace "settings: ${settings}"
    log.trace "state: ${state}"
}





