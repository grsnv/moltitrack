package com.example.model

data class MidiPatchInfo(
    val programNumber: Int, // 0 to 127
    val name: String,
    val category: String,
    val synthFamily: SynthInstrument
)

object GeneralMidiPatches {

    val allPatches: List<MidiPatchInfo> = listOf(
        // 1. PIANO
        MidiPatchInfo(0, "Acoustic Grand Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(1, "Bright Acoustic Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(2, "Electric Grand Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(3, "Honky-tonk Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(4, "Rhodes Electric Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(5, "Chorused FM Piano", "Piano", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(6, "Harpsichord", "Piano", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(7, "Clavinet", "Piano", SynthInstrument.SYNTH_LEAD),

        // 2. CHROMATIC PERCUSSION
        MidiPatchInfo(8, "Celesta", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(9, "Glockenspiel", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(10, "Music Box", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(11, "Vibraphone", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(12, "Marimba", "Chromatic", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(13, "Xylophone", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(14, "Tubular Bells", "Chromatic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(15, "Dulcimer", "Chromatic", SynthInstrument.SYNTH_LEAD),

        // 3. ORGAN
        MidiPatchInfo(16, "Drawbar Organ", "Organ", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(17, "Percussive Organ", "Organ", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(18, "Rock Organ", "Organ", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(19, "Church Pipe Organ", "Organ", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(20, "Reed Organ", "Organ", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(21, "Accordion", "Organ", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(22, "Harmonica", "Organ", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(23, "Tango Accordion", "Organ", SynthInstrument.SYNTH_LEAD),

        // 4. GUITAR
        MidiPatchInfo(24, "Acoustic Guitar (Nylon)", "Guitar", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(25, "Acoustic Guitar (Steel)", "Guitar", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(26, "Electric Guitar (Jazz)", "Guitar", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(27, "Electric Guitar (Clean)", "Guitar", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(28, "Electric Guitar (Muted)", "Guitar", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(29, "Overdriven Guitar", "Guitar", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(30, "Distortion Guitar", "Guitar", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(31, "Guitar Harmonics", "Guitar", SynthInstrument.ELECTRIC_PIANO),

        // 5. BASS
        MidiPatchInfo(32, "Acoustic Upright Bass", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(33, "Electric Finger Bass", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(34, "Electric Pick Bass", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(35, "Fretless Bass", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(36, "Slap Bass 1", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(37, "Slap Bass 2", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(38, "Analog Synth Bass 1", "Bass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(39, "Sub Moog Bass 2", "Bass", SynthInstrument.BASS_SYNTH),

        // 6. STRINGS
        MidiPatchInfo(40, "Violin", "Strings", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(41, "Viola", "Strings", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(42, "Cello", "Strings", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(43, "Contrabass", "Strings", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(44, "Tremolo Strings", "Strings", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(45, "Pizzicato Strings", "Strings", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(46, "Orchestral Harp", "Strings", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(47, "Timpani", "Strings", SynthInstrument.BASS_SYNTH),

        // 7. ENSEMBLE
        MidiPatchInfo(48, "String Ensemble 1", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(49, "String Ensemble 2", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(50, "Synth Strings 1", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(51, "Synth Strings 2", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(52, "Choir Aahs", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(53, "Voice Oohs", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(54, "Synth Voice Choir", "Ensemble", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(55, "Orchestra Hit", "Ensemble", SynthInstrument.SYNTH_LEAD),

        // 8. BRASS
        MidiPatchInfo(56, "Trumpet", "Brass", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(57, "Trombone", "Brass", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(58, "Tuba", "Brass", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(59, "Muted Trumpet", "Brass", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(60, "French Horn", "Brass", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(61, "Brass Section", "Brass", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(62, "Synth Brass 1", "Brass", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(63, "Synth Brass 2", "Brass", SynthInstrument.SYNTH_LEAD),

        // 9. REED
        MidiPatchInfo(64, "Soprano Sax", "Reed", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(65, "Alto Sax", "Reed", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(66, "Tenor Sax", "Reed", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(67, "Baritone Sax", "Reed", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(68, "Oboe", "Reed", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(69, "English Horn", "Reed", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(70, "Bassoon", "Reed", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(71, "Clarinet", "Reed", SynthInstrument.SYNTH_LEAD),

        // 10. PIPE
        MidiPatchInfo(72, "Piccolo", "Pipe", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(73, "Flute", "Pipe", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(74, "Recorder", "Pipe", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(75, "Pan Flute", "Pipe", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(76, "Blown Bottle", "Pipe", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(77, "Shakuhachi", "Pipe", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(78, "Whistle", "Pipe", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(79, "Ocarina", "Pipe", SynthInstrument.PAD_STRINGS),

        // 11. SYNTH LEAD
        MidiPatchInfo(80, "Lead 1 (Square)", "Synth Lead", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(81, "Lead 2 (Sawtooth)", "Synth Lead", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(82, "Lead 3 (Calliope)", "Synth Lead", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(83, "Lead 4 (Chiff)", "Synth Lead", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(84, "Lead 5 (Charang)", "Synth Lead", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(85, "Lead 6 (Voice)", "Synth Lead", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(86, "Lead 7 (Fifths)", "Synth Lead", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(87, "Lead 8 (Bass + Lead)", "Synth Lead", SynthInstrument.BASS_SYNTH),

        // 12. SYNTH PAD
        MidiPatchInfo(88, "Pad 1 (New Age)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(89, "Pad 2 (Warm Analog)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(90, "Pad 3 (Polysynth)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(91, "Pad 4 (Choir Space)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(92, "Pad 5 (Bowed Glass)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(93, "Pad 6 (Metallic)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(94, "Pad 7 (Halo Aura)", "Synth Pad", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(95, "Pad 8 (Sweep Filter)", "Synth Pad", SynthInstrument.PAD_STRINGS),

        // 13. SYNTH SFX
        MidiPatchInfo(96, "FX 1 (Rain)", "Synth FX", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(97, "FX 2 (Soundtrack)", "Synth FX", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(98, "FX 3 (Crystal Bell)", "Synth FX", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(99, "FX 4 (Atmosphere)", "Synth FX", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(100, "FX 5 (Brightness)", "Synth FX", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(101, "FX 6 (Goblins)", "Synth FX", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(102, "FX 7 (Echoes)", "Synth FX", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(103, "FX 8 (Sci-Fi)", "Synth FX", SynthInstrument.RETRO_8BIT),

        // 14. ETHNIC
        MidiPatchInfo(104, "Sitar", "Ethnic", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(105, "Banjo", "Ethnic", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(106, "Shamisen", "Ethnic", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(107, "Koto", "Ethnic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(108, "Kalimba", "Ethnic", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(109, "Bagpipe", "Ethnic", SynthInstrument.SYNTH_LEAD),
        MidiPatchInfo(110, "Fiddle", "Ethnic", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(111, "Shanai", "Ethnic", SynthInstrument.SYNTH_LEAD),

        // 15. PERCUSSIVE
        MidiPatchInfo(112, "Tinkle Bell", "Percussive", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(113, "Agogo", "Percussive", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(114, "Steel Drums", "Percussive", SynthInstrument.ELECTRIC_PIANO),
        MidiPatchInfo(115, "Woodblock", "Percussive", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(116, "Taiko Drum", "Percussive", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(117, "Melodic Tom", "Percussive", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(118, "Synth Drum", "Percussive", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(119, "Reverse Cymbal", "Percussive", SynthInstrument.PAD_STRINGS),

        // 16. SOUND EFFECTS
        MidiPatchInfo(120, "Guitar Fret Noise", "Sound Effects", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(121, "Breath Noise", "Sound Effects", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(122, "Seashore", "Sound Effects", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(123, "Bird Tweet", "Sound Effects", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(124, "Telephone Ring", "Sound Effects", SynthInstrument.RETRO_8BIT),
        MidiPatchInfo(125, "Helicopter", "Sound Effects", SynthInstrument.BASS_SYNTH),
        MidiPatchInfo(126, "Applause", "Sound Effects", SynthInstrument.PAD_STRINGS),
        MidiPatchInfo(127, "Gunshot", "Sound Effects", SynthInstrument.BASS_SYNTH)
    )

    val categories: List<String> = allPatches.map { it.category }.distinct()

    fun findPatch(program: Int): MidiPatchInfo {
        return allPatches.firstOrNull { it.programNumber == program } ?: allPatches[0]
    }
}
